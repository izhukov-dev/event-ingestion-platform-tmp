package com.contentaggregator.ingestion.outbox;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.contentaggregator.ingestion.outbox.OutboxEventRepository.OutboxRecord;

@Component
public class OutboxPublisherScheduler {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisherScheduler.class);
  private static final long KAFKA_ACK_TIMEOUT_SECONDS = 5;
  private static final int BATCH_SIZE = 50;
  private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
  private static final Duration DEFAULT_RETENTION_PERIOD = Duration.ofDays(7);

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  public OutboxPublisherScheduler(
      OutboxEventRepository outboxEventRepository, KafkaTemplate<String, Object> kafkaTemplate) {
    this.outboxEventRepository = outboxEventRepository;
    this.kafkaTemplate = kafkaTemplate;
  }

  @Scheduled(fixedDelayString = "${ingestion.outbox.poll-interval:1000}")
  public int publishPendingEvents() {
    List<OutboxRecord> pending =
        outboxEventRepository.claimPendingBatch(BATCH_SIZE, LEASE_DURATION);
    if (pending.isEmpty()) {
      return 0;
    }

    int publishedCount = 0;
    for (OutboxRecord record : pending) {
      try {
        kafkaTemplate
            .send("content-discovered", record.aggregateId(), record.payload())
            .get(KAFKA_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        outboxEventRepository.markProcessed(record.id());
        publishedCount++;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error(
            "Thread interrupted while awaiting Kafka broker ACK for outbox record: {}",
            record.id(),
            e);
        break;
      } catch (ExecutionException | TimeoutException | RuntimeException e) {
        log.error("Failed to publish outbox record {} to Kafka", record.id(), e);
        outboxEventRepository.markFailed(record.id(), e.getMessage());
      }
    }

    return publishedCount;
  }

  @Scheduled(cron = "${ingestion.outbox.purge-cron:0 0 * * * *}")
  public int purgeOldProcessedEvents() {
    return outboxEventRepository.purgeProcessedEvents(DEFAULT_RETENTION_PERIOD);
  }
}
