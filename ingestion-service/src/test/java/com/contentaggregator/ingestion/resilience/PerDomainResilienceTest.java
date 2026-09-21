package com.contentaggregator.ingestion.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.contentaggregator.annotations.UnitTest;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

@UnitTest
class PerDomainResilienceTest {

  private final PerDomainResilienceService resilienceService =
      new PerDomainResilienceService(5, 50.0f, 10, Duration.ofSeconds(10));

  @Test
  @DisplayName("RateLimiter enforces max 5 calls per second per domain")
  void shouldEnforceRateLimiterPerDomain() {
    String domain = "api.ratelimited.com";
    AtomicInteger counter = new AtomicInteger();

    // First 5 calls pass
    for (int i = 0; i < 5; i++) {
      String result = resilienceService.execute(domain, () -> "ok-" + counter.incrementAndGet());
      assertThat(result).startsWith("ok-");
    }

    // 6th call should be rejected or throttled
    assertThatThrownBy(() -> resilienceService.execute(domain, () -> "overflow"))
        .isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName(
      "CircuitBreaker opens on 50% failure rate for a specific domain while other domains remain unaffected")
  void shouldIsolateFailuresByDomain() {
    String failingDomain = "broken.example.com";
    String healthyDomain = "healthy.example.com";

    // Cause consecutive failures on broken domain to trip circuit breaker
    for (int i = 0; i < 10; i++) {
      try {
        resilienceService.execute(
            failingDomain,
            () -> {
              throw new RuntimeException("HTTP 500");
            });
      } catch (Exception ignored) {
      }
    }

    // Next call on broken domain must immediately throw CallNotPermittedException
    assertThatThrownBy(() -> resilienceService.execute(failingDomain, () -> "fail"))
        .isInstanceOf(CallNotPermittedException.class);

    // Healthy domain must continue functioning with 0 errors
    String healthyResult = resilienceService.execute(healthyDomain, () -> "success");
    assertThat(healthyResult).isEqualTo("success");
  }
}
