package com.contentaggregator.core.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.contentaggregator.annotations.KafkaTest;
import com.contentaggregator.events.ContentDiscoveredEvent;
import com.contentaggregator.testutil.containers.KafkaContainersConfig;
import com.contentaggregator.testutil.containers.PostgresContainersConfig;
import com.contentaggregator.testutil.containers.RedisContainersConfig;

@KafkaTest
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainersConfig.class, KafkaContainersConfig.class, RedisContainersConfig.class})
class PoisonPillDeadLetterPublishingIntegrationTest {

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private KafkaConnectionDetails kafkaConnectionDetails;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.execute("DELETE FROM content_items WHERE source_id = 'src-poison'");
  }

  @Test
  @DisplayName(
      "Poison pill invalid payload must be routed to DLT with error headers and without killing consumer")
  void shouldSurvivePoisonPillAndProcessSubsequentValidEvent() {
    String sourceId = "src-poison";
    String validExternalId = "ext-valid-after-poison-102";
    String poisonPayload = "{invalid-json-payload-poison-pill";

    // 1. Send poison pill (malformed JSON string)
    kafkaTemplate.send("content-discovered", sourceId, poisonPayload);

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

    // 4. Verify poison pill routed to DLT with standard Spring Kafka error headers
    Map<String, Object> consumerProps =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaConnectionDetails.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "dlt-verifier-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);

    try (Consumer<String, String> dltConsumer =
        new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
      dltConsumer.subscribe(Collections.singletonList("content-discovered.DLT"));

      java.util.List<ConsumerRecord<String, String>> receivedRecords = new java.util.ArrayList<>();
      await()
          .atMost(15, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofMillis(500));
                records.forEach(receivedRecords::add);
                assertThat(receivedRecords).isNotEmpty();
                ConsumerRecord<String, String> dltRecord = receivedRecords.get(0);

                String rawValue = dltRecord.value();
                if (rawValue != null && rawValue.startsWith("\"") && rawValue.endsWith("\"")) {
                  rawValue = rawValue.substring(1, rawValue.length() - 1);
                }
                String decodedPayload;
                try {
                  decodedPayload =
                      new String(Base64.getDecoder().decode(rawValue), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ignored) {
                  decodedPayload = dltRecord.value();
                }
                assertThat(decodedPayload).contains(poisonPayload);

                Header exceptionFqcnHeader =
                    dltRecord.headers().lastHeader("kafka_dlt-exception-fqcn");
                assertThat(exceptionFqcnHeader).isNotNull();
                String exceptionFqcn =
                    new String(exceptionFqcnHeader.value(), StandardCharsets.UTF_8);
                assertThat(exceptionFqcn).contains("Exception");
              });
    }
  }
}
