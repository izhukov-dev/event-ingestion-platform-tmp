package com.contentaggregator.ingestion.parser;

import java.time.LocalDateTime;

public record ParsedFeedItem(
    String sourceId,
    String externalId,
    String title,
    String url,
    String contentText,
    LocalDateTime publishedAt) {}
