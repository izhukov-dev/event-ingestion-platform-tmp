package com.contentaggregator.core.user;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class UserFeedVersionService {

  private static final Logger log = LoggerFactory.getLogger(UserFeedVersionService.class);
  private static final Duration VERSION_LOCAL_TTL = Duration.ofSeconds(60);

  private final Optional<StringRedisTemplate> redisTemplate;
  private final Cache<UUID, Long> localVersionCache;
  private final ConcurrentHashMap<UUID, AtomicLong> fallbackVersionMap = new ConcurrentHashMap<>();

  public UserFeedVersionService() {
    this(null);
  }

  @Autowired
  public UserFeedVersionService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
    this.redisTemplate = Optional.ofNullable(redisTemplate);
    this.localVersionCache = Caffeine.newBuilder().expireAfterWrite(VERSION_LOCAL_TTL).build();
  }

  public long getVersion(UUID userId) {
    Long cached = localVersionCache.getIfPresent(userId);
    if (cached != null) {
      return cached;
    }

    long version = fetchVersionFromRedisOrFallback(userId);
    localVersionCache.put(userId, version);
    return version;
  }

  public long incrementVersion(UUID userId) {
    localVersionCache.invalidate(userId);
    if (redisTemplate.isPresent()) {
      try {
        String key = "user:" + userId + ":feed_version";
        Long incremented = redisTemplate.get().opsForValue().increment(key);
        if (incremented != null) {
          localVersionCache.put(userId, incremented);
          return incremented;
        }
      } catch (Exception e) {
        log.warn(
            "Failed to increment user feed version in Redis for user {}: {}",
            userId,
            e.getMessage());
      }
    }

    long fallback =
        fallbackVersionMap.computeIfAbsent(userId, k -> new AtomicLong(1L)).incrementAndGet();
    localVersionCache.put(userId, fallback);
    return fallback;
  }

  private long fetchVersionFromRedisOrFallback(UUID userId) {
    if (redisTemplate.isPresent()) {
      try {
        String key = "user:" + userId + ":feed_version";
        String val = redisTemplate.get().opsForValue().get(key);
        if (val != null) {
          return Long.parseLong(val);
        }
        // Initialize key in Redis
        redisTemplate.get().opsForValue().set(key, "1");
        return 1L;
      } catch (Exception e) {
        log.warn(
            "Failed to fetch user feed version from Redis for user {}: {}", userId, e.getMessage());
      }
    }
    return fallbackVersionMap.computeIfAbsent(userId, k -> new AtomicLong(1L)).get();
  }
}
