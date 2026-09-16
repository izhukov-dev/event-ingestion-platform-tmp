package com.contentaggregator.core.content;

import java.time.LocalDateTime;
import java.util.UUID;

public record FeedItemResponse(
    UUID id,
    String sourceId,
    String externalId,
    String title,
    String url,
    String cleanContent,
    LocalDateTime publishedAt) {

  public static FeedItemResponse fromDomain(ContentItem item) {
    return new FeedItemResponse(
        item.id(),
        item.sourceId(),
        item.externalId(),
        item.title(),
        item.url(),
        item.cleanContent(),
        item.publishedAt());
  }
}
