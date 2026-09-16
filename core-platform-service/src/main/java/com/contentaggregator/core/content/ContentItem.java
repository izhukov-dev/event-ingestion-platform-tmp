package com.contentaggregator.core.content;

import java.time.LocalDateTime;
import java.util.UUID;

public record ContentItem(
    UUID id,
    String sourceId,
    String externalId,
    String title,
    String url,
    String cleanContent,
    LocalDateTime publishedAt) {}
