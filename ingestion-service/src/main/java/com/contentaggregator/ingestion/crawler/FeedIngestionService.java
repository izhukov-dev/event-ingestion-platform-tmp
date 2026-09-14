package com.contentaggregator.ingestion.crawler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.contentaggregator.events.ContentDiscoveredEvent;
import com.contentaggregator.ingestion.client.IngestionHttpClient;
import com.contentaggregator.ingestion.outbox.OutboxEventRepository;
import com.contentaggregator.ingestion.parser.JsoupFeedParser;
import com.contentaggregator.ingestion.parser.ParsedFeedItem;
import com.contentaggregator.ingestion.resilience.PerDomainResilienceService;

@Service
public class FeedIngestionService {

  private static final Logger log = LoggerFactory.getLogger(FeedIngestionService.class);
  private static final String CONTENT_TYPE_RSS = "RSS";
  private static final String EVENT_TYPE_DISCOVERED = "ContentDiscoveredEvent";

  private final IngestionHttpClient httpClient;
  private final JsoupFeedParser feedParser;
  private final PerDomainResilienceService resilienceService;
  private final OutboxEventRepository outboxRepository;
  private final IngestionCrawlerProperties crawlerProperties;

  public FeedIngestionService(
      IngestionHttpClient httpClient,
      JsoupFeedParser feedParser,
      PerDomainResilienceService resilienceService,
      OutboxEventRepository outboxRepository,
      IngestionCrawlerProperties crawlerProperties) {
    this.httpClient = httpClient;
    this.feedParser = feedParser;
    this.resilienceService = resilienceService;
    this.outboxRepository = outboxRepository;
    this.crawlerProperties = crawlerProperties;
  }

  public int ingestAllFeeds() {
    if (!crawlerProperties.enabled() || crawlerProperties.sources() == null) {
      return 0;
    }

    int totalIngested = 0;
    for (IngestionCrawlerProperties.FeedSource source : crawlerProperties.sources()) {
      try {
        totalIngested += ingestFeed(source);
      } catch (Exception e) {
        log.error("Failed to ingest feed from {}: {}", source.url(), e.getMessage());
      }
    }
    return totalIngested;
  }

  public int ingestFeed(IngestionCrawlerProperties.FeedSource source) {
    String domain = extractDomain(source.url());
    log.info("Starting ingestion for source: {} ({})", source.id(), source.url());

    byte[] payload = resilienceService.execute(domain, () -> httpClient.fetch(source.url()));

    List<ParsedFeedItem> items = feedParser.parse(source.id(), payload);
    log.info("Parsed {} items from {}", items.size(), source.id());

    int newItemsCount = 0;
    for (ParsedFeedItem item : items) {
      if (persistOutboxEvent(item)) {
        newItemsCount++;
      }
    }

    log.info("Ingested {} new unique items from {}", newItemsCount, source.id());
    return newItemsCount;
  }

  private boolean persistOutboxEvent(ParsedFeedItem item) {
    try {
      String rawKey = item.sourceId() + ":" + item.externalId();
      String aggregateId =
          UUID.nameUUIDFromBytes(rawKey.getBytes(StandardCharsets.UTF_8)).toString();

      ContentDiscoveredEvent event =
          ContentDiscoveredEvent.from(
              item.sourceId(),
              item.externalId(),
              CONTENT_TYPE_RSS,
              item.title(),
              item.url(),
              item.publishedAt());

      String json = formatEventJson(event);
      int rows = outboxRepository.insertIfAbsent(aggregateId, EVENT_TYPE_DISCOVERED, json);
      return rows > 0;
    } catch (Exception e) {
      log.warn("Failed to persist outbox event for item {}: {}", item.externalId(), e.getMessage());
      return false;
    }
  }

  private String formatEventJson(ContentDiscoveredEvent event) {
    LocalDateTime pub = event.publishedAt() != null ? event.publishedAt() : event.timestamp();
    return "{\"eventId\":\""
        + escapeJson(event.eventId())
        + "\",\"timestamp\":\""
        + event.timestamp()
        + "\",\"sourceId\":\""
        + escapeJson(event.sourceId())
        + "\",\"externalId\":\""
        + escapeJson(event.externalId())
        + "\",\"contentType\":\""
        + escapeJson(event.contentType())
        + "\",\"title\":\""
        + escapeJson(event.title())
        + "\",\"url\":\""
        + escapeJson(event.url())
        + "\",\"publishedAt\":\""
        + pub
        + "\"}";
  }

  private String escapeJson(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t");
  }

  private String extractDomain(String url) {
    try {
      URI uri = URI.create(url);
      String host = uri.getHost();
      return host != null ? host : "default";
    } catch (Exception e) {
      return "default";
    }
  }
}
