package com.contentaggregator.core.enrichment;

import java.time.LocalDateTime;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.contentaggregator.events.ContentDiscoveredEvent;

@Component
public class ContentDiscoveredConsumer {

  private static final Logger log = LoggerFactory.getLogger(ContentDiscoveredConsumer.class);

  private static final String INSERT_CONTENT_SQL =
      """
      INSERT INTO content_items (
          id,
          source_id,
          external_id,
          title,
          url,
          content_text,
          published_at,
          embedding
      ) VALUES (
          gen_random_uuid(),
          ?,
          ?,
          ?,
          ?,
          ?,
          ?,
          ?::halfvec
      )
      ON CONFLICT (source_id, external_id) DO NOTHING
      """;

  private final ContentEnrichmentService enrichmentService;
  private final JdbcTemplate jdbcTemplate;

  public ContentDiscoveredConsumer(
      ContentEnrichmentService enrichmentService, JdbcTemplate jdbcTemplate) {
    this.enrichmentService = enrichmentService;
    this.jdbcTemplate = jdbcTemplate;
  }

  @KafkaListener(topics = "content-discovered", groupId = "core-enrichment-group")
  public void consume(ContentDiscoveredEvent event) {
    log.info(
        "Received ContentDiscoveredEvent: source={}, externalId={}",
        event.sourceId(),
        event.externalId());

    EnrichedContent enriched = enrichmentService.enrich(event.title(), event.url(), event.title());

    String vectorStr = enriched.embedding() != null ? Arrays.toString(enriched.embedding()) : null;
    LocalDateTime published =
        event.publishedAt() != null ? event.publishedAt() : LocalDateTime.now();

    int rowsAffected =
        jdbcTemplate.update(
            INSERT_CONTENT_SQL,
            event.sourceId(),
            event.externalId(),
            enriched.title(),
            enriched.url(),
            enriched.contentText(),
            published,
            vectorStr);

    if (rowsAffected > 0) {
      log.info(
          "Successfully persisted content item: {} (status: {})",
          event.externalId(),
          enriched.status());
    } else {
      log.info("Duplicate content item ignored via ON CONFLICT: {}", event.externalId());
    }
  }
}
