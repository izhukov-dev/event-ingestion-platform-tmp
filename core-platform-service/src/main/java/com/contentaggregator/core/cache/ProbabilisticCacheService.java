package com.contentaggregator.core.cache;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class ProbabilisticCacheService {

  private static final Logger log = LoggerFactory.getLogger(ProbabilisticCacheService.class);
  private static final double DEFAULT_BETA = 1.0;
  private static final double MIN_RANDOM_VALUE = 0.0001;
  private static final long DEFAULT_MAX_CACHE_SIZE = 10_000;

  private final Clock clock;
  private final Cache<String, CacheEntry<?>> localCache;
  private final Optional<StringRedisTemplate> redisTemplate;
  private final ObjectMapper objectMapper;
  private final ConcurrentHashMap<String, CompletableFuture<?>> inFlightRecomputations =
      new ConcurrentHashMap<>();
  private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public ProbabilisticCacheService() {
    this(Clock.systemUTC(), DEFAULT_MAX_CACHE_SIZE, null, null);
  }

  public ProbabilisticCacheService(Clock clock) {
    this(clock, DEFAULT_MAX_CACHE_SIZE, null, null);
  }

  public ProbabilisticCacheService(Clock clock, long maxCacheSize) {
    this(clock, maxCacheSize, null, null);
  }

  @Autowired
  public ProbabilisticCacheService(
      @Autowired(required = false) StringRedisTemplate redisTemplate,
      @Autowired(required = false) ObjectMapper objectMapper) {
    this(Clock.systemUTC(), DEFAULT_MAX_CACHE_SIZE, redisTemplate, objectMapper);
  }

  public ProbabilisticCacheService(
      Clock clock,
      long maxCacheSize,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper) {
    this.clock = clock;
    this.localCache = Caffeine.newBuilder().maximumSize(maxCacheSize).build();
    this.redisTemplate = Optional.ofNullable(redisTemplate);
    if (objectMapper != null) {
      this.objectMapper = objectMapper;
    } else {
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      this.objectMapper = mapper;
    }
  }

  public <T> T getOrCompute(
      String key, TypeReference<T> typeRef, Duration ttl, Duration delta, Supplier<T> loader) {
    JavaType javaType =
        typeRef != null ? objectMapper.getTypeFactory().constructType(typeRef) : null;
    return getOrComputeInternal(key, javaType, ttl, delta, loader);
  }

  public <T> T getOrCompute(
      String key, Class<T> clazz, Duration ttl, Duration delta, Supplier<T> loader) {
    JavaType javaType = clazz != null ? objectMapper.getTypeFactory().constructType(clazz) : null;
    return getOrComputeInternal(key, javaType, ttl, delta, loader);
  }

  public <T> T getOrCompute(String key, Duration ttl, Duration delta, Supplier<T> loader) {
    return getOrComputeInternal(key, null, ttl, delta, loader);
  }

  private <T> T getOrComputeInternal(
      String key, JavaType javaType, Duration ttl, Duration delta, Supplier<T> loader) {
    long now = clock.millis();
    @SuppressWarnings("unchecked")
    CacheEntry<T> entry = (CacheEntry<T>) localCache.getIfPresent(key);

    if (entry != null) {
      long ttlRemaining = entry.expiryEpochMs() - now;
      if (ttlRemaining > 0) {
        if (shouldRecomputeEarly(ttlRemaining, ttl, delta)) {
          triggerAsynchronousRecompute(key, entry.expiryEpochMs(), ttl, delta, loader);
        }
        return entry.value();
      }
    }

    // L2 Redis Check
    if (redisTemplate.isPresent()) {
      T redisVal = readFromRedis(key, javaType);
      if (redisVal != null) {
        long expiry = now + ttl.toMillis();
        localCache.put(key, new CacheEntry<>(redisVal, expiry, delta.toMillis()));
        return redisVal;
      }
    }

    return computeSynchronous(key, ttl, delta, loader);
  }

  @SuppressWarnings("unchecked")
  private <T> T readFromRedis(String key, JavaType javaType) {
    try {
      String json = redisTemplate.get().opsForValue().get(key);
      if (json == null) {
        return null;
      }
      if (javaType != null) {
        return objectMapper.readValue(json, javaType);
      }
      return (T) objectMapper.readValue(json, Object.class);
    } catch (Exception e) {
      log.warn("Failed to read key {} from Redis L2 cache: {}", key, e.getMessage());
      return null;
    }
  }

  private <T> void writeToRedis(String key, T value, Duration ttl) {
    if (redisTemplate.isEmpty() || value == null) {
      return;
    }
    try {
      String json = objectMapper.writeValueAsString(value);
      redisTemplate.get().opsForValue().set(key, json, ttl);
    } catch (Exception e) {
      log.warn("Failed to write key {} to Redis L2 cache: {}", key, e.getMessage());
    }
  }

  private boolean shouldRecomputeEarly(long ttlRemainingMs, Duration ttl, Duration delta) {
    if (ttlRemainingMs > ttl.toMillis() - delta.toMillis()) {
      return false;
    }
    double rand = ThreadLocalRandom.current().nextDouble(MIN_RANDOM_VALUE, 1.0);
    double xfetchThreshold = -DEFAULT_BETA * delta.toMillis() * Math.log(rand);
    return xfetchThreshold > ttlRemainingMs;
  }

  private <T> void triggerAsynchronousRecompute(
      String key, long currentEntryExpiry, Duration ttl, Duration delta, Supplier<T> loader) {
    inFlightRecomputations.computeIfAbsent(
        key,
        k -> {
          CacheEntry<?> current = localCache.getIfPresent(k);
          if (current != null && current.expiryEpochMs() > currentEntryExpiry) {
            return null;
          }
          CompletableFuture<Void> future =
              CompletableFuture.runAsync(
                  () -> {
                    try {
                      T freshValue = loader.get();
                      long newExpiry = clock.millis() + ttl.toMillis();
                      localCache.put(k, new CacheEntry<>(freshValue, newExpiry, delta.toMillis()));
                      writeToRedis(k, freshValue, ttl);
                    } catch (Exception e) {
                      log.warn("Asynchronous early recomputation failed for key: {}", k, e);
                    }
                  },
                  virtualThreadExecutor);
          future.whenComplete((res, err) -> inFlightRecomputations.remove(k));
          return future;
        });
  }

  private <T> T computeSynchronous(String key, Duration ttl, Duration delta, Supplier<T> loader) {
    T value = loader.get();
    long expiry = clock.millis() + ttl.toMillis();
    localCache.put(key, new CacheEntry<>(value, expiry, delta.toMillis()));
    writeToRedis(key, value, ttl);
    return value;
  }

  public long estimatedSize() {
    return localCache.estimatedSize();
  }

  public void cleanUp() {
    localCache.cleanUp();
  }

  private record CacheEntry<T>(T value, long expiryEpochMs, long deltaMs) {}
}
