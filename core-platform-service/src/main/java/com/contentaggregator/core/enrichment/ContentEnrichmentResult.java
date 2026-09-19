package com.contentaggregator.core.enrichment;

import java.util.List;

public record ContentEnrichmentResult(
    String summary, List<String> tags, String primaryCategory, int estimatedReadMinutes) {}
