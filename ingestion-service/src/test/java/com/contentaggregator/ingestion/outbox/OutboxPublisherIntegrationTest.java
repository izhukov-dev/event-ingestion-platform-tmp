package com.contentaggregator.ingestion.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.contentaggregator.annotations.KafkaTest;
import com.contentaggregator.testutil.containers.KafkaContainersConfig;
import com.contentaggregator.testutil.containers.PostgresContainersConfig;

@KafkaTest
@SpringBootTest
@Import({PostgresContainersConfig.class, KafkaContainersConfig.class})
@ActiveProfiles("test")
class OutboxPublisherIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private OutboxPublisherScheduler publisherScheduler;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.execute("TRUNCATE TABLE outbox_events CASCADE");
  }

  @Test
  @DisplayName(
      "Outbox poller must claim pending event via atomic CTE lease, publish to Kafka and mark processed")
  void shouldPublishPendingEventToKafkaAndMarkProcessed() {
    UUID eventId = UUID.randomUUID();
    String payloadJson =
        """
        {
          "eventId": "%s",
          "timestamp": "2026-09-11T12:00:00",
          "sourceId": "src-outbox-test",
          "externalId": "ext-outbox-999",
          "contentType": "RSS",
          "title": "Outbox Event Test",
          "url": "https://example.com/outbox",
          "publishedAt": "2026-09-11T12:00:00"
        }
        """
            .formatted(eventId);

    // 1. Insert unprocessed outbox event
    jdbcTemplate.update(
        "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, type, payload, created_at, processed_at) "
            + "VALUES (?, 'ContentItem', ?, 'ContentDiscoveredEvent', ?::jsonb, now(), null)",
        eventId,
        "ext-outbox-999",
        payloadJson);

    // 2. Trigger publisher scheduler
    int publishedCount = publisherScheduler.publishPendingEvents();
    assertThat(publishedCount).isGreaterThanOrEqualTo(1);

    // 3. Verify event is marked as processed in DB and locked_until was set during claim
    String processedAt =
        jdbcTemplate.queryForObject(
            "SELECT processed_at::text FROM outbox_events WHERE id = ?", String.class, eventId);
    assertThat(processedAt).isNotNull();

    String lockedUntil =
        jdbcTemplate.queryForObject(
            "SELECT locked_until::text FROM outbox_events WHERE id = ?", String.class, eventId);
    assertThat(lockedUntil).isNotNull();
  }

  @Test
  @DisplayName("Retention purge must delete events processed older than retention threshold")
  void shouldPurgeOldProcessedEvents() {
    UUID oldEventId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, type, payload, created_at, processed_at) "
            + "VALUES (?, 'ContentItem', 'old-agg', 'ContentDiscoveredEvent', '{\"title\":\"Old\"}'::jsonb, now() - interval '10 days', now() - interval '8 days')",
        oldEventId);

    int purgedCount = publisherScheduler.purgeOldProcessedEvents();
    assertThat(purgedCount).isGreaterThanOrEqualTo(1);

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE id = ?", Integer.class, oldEventId);
    assertThat(count).isZero();
  }
}
