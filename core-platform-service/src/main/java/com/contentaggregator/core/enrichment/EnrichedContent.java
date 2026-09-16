package com.contentaggregator.core.enrichment;

import java.util.List;

public record EnrichedContent(
    String title,
    String url,
    String contentText,
    String summary,
    List<String> tags,
    String primaryCategory,
    int estimatedReadMinutes,
    float[] embedding,
    EnrichmentStatus status) {}
