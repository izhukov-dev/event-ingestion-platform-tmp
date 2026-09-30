package com.contentaggregator.core.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.contentaggregator.annotations.DatabaseTest;
import com.contentaggregator.core.content.FeedItemResponse;
import com.contentaggregator.testutil.containers.KafkaContainersConfig;
import com.contentaggregator.testutil.containers.PostgresContainersConfig;
import com.contentaggregator.testutil.containers.RedisContainersConfig;
import com.fasterxml.jackson.core.type.TypeReference;

@DatabaseTest
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainersConfig.class, KafkaContainersConfig.class, RedisContainersConfig.class})
class ProbabilisticCacheRedisIntegrationTest {

  @Autowired private ProbabilisticCacheService cacheService;

  @Autowired private StringRedisTemplate redisTemplate;

  @Test
  @DisplayName(
      "Two-level cache must populate Redis L2 on compute and retrieve from Redis on L1 eviction")
  void shouldPopulateAndReadFromRedisL2() {
    String key = "test:two-level:item-" + System.currentTimeMillis();
    AtomicInteger loaderCallCount = new AtomicInteger(0);

    // 1. Initial compute: writes to L1 Caffeine and L2 Redis
    String value1 =
        cacheService.getOrCompute(
            key,
            Duration.ofMinutes(5),
            Duration.ofMillis(50),
            () -> {
              loaderCallCount.incrementAndGet();
              return "redis-cached-payload";
            });

    assertThat(value1).isEqualTo("redis-cached-payload");
    assertThat(loaderCallCount.get()).isEqualTo(1);

    // Verify key exists in Redis L2
    String redisRaw = redisTemplate.opsForValue().get(key);
    assertThat(redisRaw).isNotNull();
    assertThat(redisRaw).contains("redis-cached-payload");

    // 2. Create a fresh cache service instance with empty L1 to force L2 Redis fetch
    ProbabilisticCacheService freshL1Service = new ProbabilisticCacheService(redisTemplate, null);

    String valueFromL2 =
        freshL1Service.getOrCompute(
            key,
            Duration.ofMinutes(5),
            Duration.ofMillis(50),
            () -> {
              loaderCallCount.incrementAndGet();
              return "unexpected-recompute";
            });

    assertThat(valueFromL2).isEqualTo("redis-cached-payload");
    // Loader MUST NOT be called again because value was fetched from Redis L2
    assertThat(loaderCallCount.get()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Two-level cache must safely deserialize generic List of Records from Redis L2 without ClassCastException")
  void shouldPopulateAndReadGenericListOfRecordsFromRedisL2() {
    String key = "test:two-level:records-" + System.currentTimeMillis();
    UUID itemId = UUID.randomUUID();
    FeedItemResponse expectedItem =
        new FeedItemResponse(
            itemId,
            "spring-blog",
            "entry-101",
            "Virtual Threads Deep Dive",
            "https://spring.io/blog/virtual-threads",
            "Clean content body",
            LocalDateTime.of(2026, 9, 23, 10, 0));

    TypeReference<List<FeedItemResponse>> listType = new TypeReference<>() {};

    // 1. Write to L1 & Redis L2
    List<FeedItemResponse> initialList =
        cacheService.getOrCompute(
            key,
            listType,
            Duration.ofMinutes(5),
            Duration.ofMillis(50),
            () -> List.of(expectedItem));

    assertThat(initialList).hasSize(1);
    assertThat(initialList.get(0).title()).isEqualTo("Virtual Threads Deep Dive");

    // 2. Read from Redis L2 using fresh service with empty L1
    ProbabilisticCacheService freshL1Service = new ProbabilisticCacheService(redisTemplate, null);

    List<FeedItemResponse> listFromL2 =
        freshL1Service.getOrCompute(
            key,
            listType,
            Duration.ofMinutes(5),
            Duration.ofMillis(50),
            () -> {
              throw new AssertionError("Loader must not be invoked on L2 cache hit!");
            });

    assertThat(listFromL2).hasSize(1);
    FeedItemResponse actualItem = listFromL2.get(0);
    // Verified: actualItem is a genuine FeedItemResponse instance, NOT a LinkedHashMap!
    assertThat(actualItem.id()).isEqualTo(itemId);
    assertThat(actualItem.title()).isEqualTo("Virtual Threads Deep Dive");
    assertThat(actualItem.url()).isEqualTo("https://spring.io/blog/virtual-threads");
  }
}
