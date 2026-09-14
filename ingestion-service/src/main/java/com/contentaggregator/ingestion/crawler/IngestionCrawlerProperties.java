package com.contentaggregator.ingestion.crawler;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ingestion.crawler")
public record IngestionCrawlerProperties(
    boolean enabled, long pollInterval, List<FeedSource> sources) {

  public record FeedSource(String id, String url) {}
}
