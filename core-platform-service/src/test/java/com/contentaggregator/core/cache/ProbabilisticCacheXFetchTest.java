package com.contentaggregator.core.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.contentaggregator.annotations.UnitTest;

@UnitTest
class ProbabilisticCacheXFetchTest {

  @Test
  @DisplayName(
      "Under 100 concurrent Virtual Threads, XFetch triggers exactly 1 background computation without stampede")
  void shouldPreventCacheStampedeUnderHighConcurrency() throws Exception {
    TestClock testClock = new TestClock(Instant.parse("2026-09-11T12:00:00Z"));
    ProbabilisticCacheService cacheService = new ProbabilisticCacheService(testClock);
    String key = "feed:user-123:page:1";
    AtomicInteger computationCounter = new AtomicInteger(0);

    // 1. Initial warm-up population: delta 50ms, ttl 200ms
    String initial =
        cacheService.getOrCompute(
            key,
            Duration.ofMillis(200),
            Duration.ofMillis(50),
            () -> {
              computationCounter.incrementAndGet();
              return "feed-content-v1";
            });
    assertThat(initial).isEqualTo("feed-content-v1");
    assertThat(computationCounter.get()).isEqualTo(1);

    // 2. Advance TestClock by 160ms so remaining TTL is 40ms (< delta 50ms, triggering early XFetch
    // probability)
    testClock.advance(Duration.ofMillis(160));

    // 3. Launch 100 concurrent Virtual Threads requesting the key
    int concurrentThreads = 100;
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch endGate = new CountDownLatch(concurrentThreads);
    List<Future<String>> results = new ArrayList<>();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < concurrentThreads; i++) {
        results.add(
            executor.submit(
                () -> {
                  startGate.await();
                  try {
                    return cacheService.getOrCompute(
                        key,
                        Duration.ofMillis(200),
                        Duration.ofMillis(50),
                        () -> {
                          computationCounter.incrementAndGet();
                          return "feed-content-v2";
                        });
                  } finally {
                    endGate.countDown();
                  }
                }));
      }

      startGate.countDown();
      boolean finishedInTime = endGate.await(5, TimeUnit.SECONDS);
      assertThat(finishedInTime).isTrue();
    }

    // All threads must have received a valid response (either v1 or v2) without blocking
    for (Future<String> f : results) {
      assertThat(f.get()).isIn("feed-content-v1", "feed-content-v2");
    }

    // Wait for the single asynchronous background computation to finish via Awaitility
    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(computationCounter.get()).isEqualTo(2));

    // Future calls now return the recomputed value
    String refreshed =
        cacheService.getOrCompute(
            key, Duration.ofMillis(200), Duration.ofMillis(50), () -> "never-called");
    assertThat(refreshed).isEqualTo("feed-content-v2");
  }

  @Test
  @DisplayName("Cache size must be bounded by configured maximumSize via Caffeine W-TinyLFU")
  void shouldBoundCacheSizeByConfiguredLimit() {
    TestClock testClock = new TestClock(Instant.parse("2026-09-11T12:00:00Z"));
    ProbabilisticCacheService boundedCache = new ProbabilisticCacheService(testClock, 5);

    for (int i = 0; i < 20; i++) {
      int idx = i;
      boundedCache.getOrCompute(
          "key-" + i, Duration.ofMinutes(10), Duration.ofSeconds(1), () -> "val-" + idx);
    }

    boundedCache.cleanUp();
    assertThat(boundedCache.estimatedSize()).isLessThanOrEqualTo(5);
  }

  private static class TestClock extends Clock {
    private final AtomicReference<Instant> currentInstant;
    private final ZoneId zone = ZoneId.of("UTC");

    TestClock(Instant initial) {
      this.currentInstant = new AtomicReference<>(initial);
    }

    void advance(Duration duration) {
      currentInstant.updateAndGet(i -> i.plus(duration));
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return currentInstant.get();
    }
  }
}
