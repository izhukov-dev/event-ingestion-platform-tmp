package com.contentaggregator.events;

import java.time.LocalDateTime;
import java.util.UUID;

public record ContentDiscoveredEvent(
    String eventId,
    LocalDateTime timestamp,
    String sourceId,
    String externalId,
    String contentType,
    String title,
    String url,
    LocalDateTime publishedAt) {

  public static ContentDiscoveredEvent from(
      String sourceId,
      String externalId,
      String contentType,
      String title,
      String url,
      LocalDateTime publishedAt) {
    return new ContentDiscoveredEvent(
        UUID.randomUUID().toString(),
        LocalDateTime.now(),
        sourceId,
        externalId,
        contentType,
        title,
        url,
        publishedAt);
  }
}
