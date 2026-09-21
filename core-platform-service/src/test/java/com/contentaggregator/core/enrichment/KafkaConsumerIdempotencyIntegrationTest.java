package com.contentaggregator.core.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.contentaggregator.annotations.IntegrationTest;
import com.contentaggregator.events.ContentDiscoveredEvent;
import com.contentaggregator.testutil.containers.KafkaContainersConfig;
import com.contentaggregator.testutil.containers.PostgresContainersConfig;

@IntegrationTest
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainersConfig.class, KafkaContainersConfig.class})
class KafkaConsumerIdempotencyIntegrationTest {

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.execute("DELETE FROM content_items WHERE source_id = 'src-idempotent'");
  }

  @Test
  @DisplayName(
      "Duplicate ContentDiscoveredEvents with identical (source_id, external_id) must result in exactly 1 database row")
  void shouldHandleDuplicateEventsIdempotently() {
    String sourceId = "src-idempotent";
    String externalId = "ext-dup-101";

    ContentDiscoveredEvent event1 =
        ContentDiscoveredEvent.from(
            sourceId,
            externalId,
            "RSS",
            "Архитектура Spring AI 1.1.8",
            "https://example.com/spring-ai-1",
            LocalDateTime.now());

    ContentDiscoveredEvent event2 =
        ContentDiscoveredEvent.from(
            sourceId,
            externalId,
            "RSS",
            "Архитектура Spring AI 1.1.8 (Дубликат)",
            "https://example.com/spring-ai-1",
            LocalDateTime.now());

    // Publish duplicates to Kafka
    kafkaTemplate.send("content-discovered", sourceId, event1);
    kafkaTemplate.send("content-discovered", sourceId, event2);

    // Wait for consumer to process both events
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Integer count =
                  jdbcTemplate.queryForObject(
                      "SELECT count(*) FROM content_items WHERE source_id = ? AND external_id = ?",
                      Integer.class,
                      sourceId,
                      externalId);
              assertThat(count).isEqualTo(1);
            });
  }
}
