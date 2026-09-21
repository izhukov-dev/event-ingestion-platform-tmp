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
class PoisonPillDeadLetterPublishingIntegrationTest {

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.execute("DELETE FROM content_items WHERE source_id = 'src-poison'");
  }

  @Test
  @DisplayName(
      "Poison pill invalid payload must be routed to DLT without killing consumer, allowing subsequent messages to process")
  void shouldSurvivePoisonPillAndProcessSubsequentValidEvent() {
    String sourceId = "src-poison";
    String validExternalId = "ext-valid-after-poison-102";

    // 1. Send poison pill (malformed JSON string)
    kafkaTemplate.send("content-discovered", sourceId, "{bad-json-poison-pill-payload");

    // 2. Send subsequent valid message
    ContentDiscoveredEvent validEvent =
        ContentDiscoveredEvent.from(
            sourceId,
            validExternalId,
            "ATOM",
            "Valid Post After Poison Pill",
            "https://example.com/valid-post",
            LocalDateTime.now());
    kafkaTemplate.send("content-discovered", sourceId, validEvent);

    // 3. Consumer must remain alive and successfully process the valid message
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Integer count =
                  jdbcTemplate.queryForObject(
                      "SELECT count(*) FROM content_items WHERE source_id = ? AND external_id = ?",
                      Integer.class,
                      sourceId,
                      validExternalId);
              assertThat(count).isEqualTo(1);
            });
  }
}
