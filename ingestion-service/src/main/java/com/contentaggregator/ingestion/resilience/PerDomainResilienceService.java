package com.contentaggregator.ingestion.resilience;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;

@Service
public class PerDomainResilienceService {

  private final int rateLimitCalls;
  private final float failureRateThreshold;
  private final int slidingWindowSize;
  private final Duration waitInOpenState;

  private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers =
      new ConcurrentHashMap<>();

  public PerDomainResilienceService() {
    this(5, 50.0f, 10, Duration.ofSeconds(10));
  }

  public PerDomainResilienceService(
      int rateLimitCalls,
      float failureRateThreshold,
      int slidingWindowSize,
      Duration waitInOpenState) {
    this.rateLimitCalls = rateLimitCalls;
    this.failureRateThreshold = failureRateThreshold;
    this.slidingWindowSize = slidingWindowSize;
    this.waitInOpenState = waitInOpenState;
  }

  public <T> T execute(String domain, Supplier<T> supplier) {
    RateLimiter rateLimiter = rateLimiters.computeIfAbsent(domain, this::createRateLimiter);
    CircuitBreaker circuitBreaker =
        circuitBreakers.computeIfAbsent(domain, this::createCircuitBreaker);

    Supplier<T> decorated = RateLimiter.decorateSupplier(rateLimiter, supplier);
    decorated = CircuitBreaker.decorateSupplier(circuitBreaker, decorated);

    return decorated.get();
  }

  private RateLimiter createRateLimiter(String domain) {
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(rateLimitCalls)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMillis(50))
            .build();
    return RateLimiter.of("rl-" + domain, config);
  }

  private CircuitBreaker createCircuitBreaker(String domain) {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(failureRateThreshold)
            .slidingWindowSize(slidingWindowSize)
            .minimumNumberOfCalls(5)
            .waitDurationInOpenState(waitInOpenState)
            .build();
    return CircuitBreaker.of("cb-" + domain, config);
  }
}
