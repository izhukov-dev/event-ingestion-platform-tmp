package com.contentaggregator.ingestion.outbox;

import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxEventRepository {

  private static final String INSERT_OUTBOX_SQL =
      """
      INSERT INTO outbox_events (id, aggregate_type, aggregate_id, type, payload, created_at)
      VALUES (gen_random_uuid(), 'CONTENT', ?, ?, ?::jsonb, now())
      ON CONFLICT (aggregate_id) DO NOTHING
      """;

  private static final String CLAIM_PENDING_BATCH_SQL =
      """
      WITH candidates AS (
          SELECT id FROM outbox_events
          WHERE processed_at IS NULL
            AND (locked_until IS NULL OR locked_until < clock_timestamp())
          ORDER BY created_at ASC
          LIMIT ?
          FOR UPDATE SKIP LOCKED
      )
      UPDATE outbox_events o
      SET locked_until = clock_timestamp() + (? * interval '1 millisecond')
      FROM candidates c
      WHERE o.id = c.id
      RETURNING o.id, o.aggregate_id, o.payload::text
      """;

  private static final String MARK_PROCESSED_SQL =
      "UPDATE outbox_events SET processed_at = clock_timestamp() WHERE id = ?";

  private static final String MARK_FAILED_SQL =
      "UPDATE outbox_events SET retry_count = retry_count + 1, error_message = ? WHERE id = ?";

  private static final String PURGE_PROCESSED_SQL =
      "DELETE FROM outbox_events WHERE processed_at < clock_timestamp() - (? * interval '1 millisecond')";

  private final JdbcTemplate jdbcTemplate;

  public OutboxEventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public int insertIfAbsent(String aggregateId, String eventType, String payloadJson) {
    return jdbcTemplate.update(INSERT_OUTBOX_SQL, aggregateId, eventType, payloadJson);
  }

  public List<OutboxRecord> claimPendingBatch(int limit, Duration leaseDuration) {
    long leaseMs = leaseDuration.toMillis();
    return jdbcTemplate.query(
        CLAIM_PENDING_BATCH_SQL,
        (ResultSet rs, int rowNum) ->
            new OutboxRecord(
                UUID.fromString(rs.getString("id")),
                rs.getString("aggregate_id"),
                rs.getString("payload")),
        limit,
        leaseMs);
  }

  public void markProcessed(UUID id) {
    jdbcTemplate.update(MARK_PROCESSED_SQL, id);
  }

  public void markFailed(UUID id, String errorMessage) {
    jdbcTemplate.update(MARK_FAILED_SQL, errorMessage, id);
  }

  public int purgeProcessedEvents(Duration olderThan) {
    return jdbcTemplate.update(PURGE_PROCESSED_SQL, olderThan.toMillis());
  }

  public record OutboxRecord(UUID id, String aggregateId, String payload) {}
}
