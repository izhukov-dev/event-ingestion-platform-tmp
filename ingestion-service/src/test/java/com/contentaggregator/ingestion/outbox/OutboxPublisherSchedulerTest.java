package com.contentaggregator.ingestion.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.contentaggregator.ingestion.outbox.OutboxEventRepository.OutboxRecord;
import com.contentaggregator.testutil.TestTags;

@Tag(TestTags.UNIT)
@Tag(TestTags.FAST)
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisherScheduler Unit Tests")
class OutboxPublisherSchedulerTest {

  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  private OutboxPublisherScheduler publisherScheduler;

  @BeforeEach
  void setUp() {
    publisherScheduler = new OutboxPublisherScheduler(outboxEventRepository, kafkaTemplate);
  }

  @Test
  @DisplayName("Should publish pending events and mark as processed when Kafka broker ACKs")
  @SuppressWarnings("unchecked")
  void shouldPublishPendingEventsAndMarkProcessedWhenKafkaAckReceived() {
    UUID eventId = UUID.randomUUID();
    String aggregateId = "ext-aggregate-1";
    String payload = "{\"title\":\"Event 1\"}";

    given(outboxEventRepository.claimPendingBatch(anyInt(), any(Duration.class)))
        .willReturn(List.of(new OutboxRecord(eventId, aggregateId, payload)));

    SendResult<String, Object> sendResult = mock(SendResult.class);
    CompletableFuture<SendResult<String, Object>> future =
        CompletableFuture.completedFuture(sendResult);
    given(kafkaTemplate.send("content-discovered", aggregateId, payload)).willReturn(future);

    int count = publisherScheduler.publishPendingEvents();

    assertThat(count).isEqualTo(1);
    verify(kafkaTemplate).send("content-discovered", aggregateId, payload);
    verify(outboxEventRepository).markProcessed(eventId);
  }

  @Test
  @DisplayName(
      "Should record failure and increment retry_count without marking processed when Kafka fails")
  @SuppressWarnings("unchecked")
  void shouldRecordFailureWhenKafkaFails() {
    UUID eventId = UUID.randomUUID();
    String aggregateId = "ext-aggregate-fail";
    String payload = "{\"title\":\"Failed Event\"}";

    given(outboxEventRepository.claimPendingBatch(anyInt(), any(Duration.class)))
        .willReturn(List.of(new OutboxRecord(eventId, aggregateId, payload)));

    CompletableFuture<SendResult<String, Object>> failedFuture =
        CompletableFuture.failedFuture(new TimeoutException("Kafka broker unreachable"));
    given(kafkaTemplate.send("content-discovered", aggregateId, payload)).willReturn(failedFuture);

    int count = publisherScheduler.publishPendingEvents();

    assertThat(count).isZero();
    verify(kafkaTemplate).send("content-discovered", aggregateId, payload);
    verify(outboxEventRepository)
        .markFailed(
            eq(eventId), eq("java.util.concurrent.TimeoutException: Kafka broker unreachable"));
    verify(outboxEventRepository, never()).markProcessed(any(UUID.class));
  }

  @Test
  @DisplayName("Should return 0 without publishing when no pending events exist")
  void shouldReturnZeroWhenNoPendingEvents() {
    given(outboxEventRepository.claimPendingBatch(anyInt(), any(Duration.class)))
        .willReturn(Collections.emptyList());

    int count = publisherScheduler.publishPendingEvents();

    assertThat(count).isZero();
    verify(kafkaTemplate, never()).send(anyString(), any(), any());
    verify(outboxEventRepository, never()).markProcessed(any(UUID.class));
  }

  @Test
  @DisplayName("Should delegate retention purge of old processed events to repository")
  void shouldDelegateRetentionPurge() {
    given(outboxEventRepository.purgeProcessedEvents(any(Duration.class))).willReturn(42);

    int purged = publisherScheduler.purgeOldProcessedEvents();

    assertThat(purged).isEqualTo(42);
    verify(outboxEventRepository).purgeProcessedEvents(Duration.ofDays(7));
  }
}
