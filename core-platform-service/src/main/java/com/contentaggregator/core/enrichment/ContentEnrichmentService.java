package com.contentaggregator.core.enrichment;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ContentEnrichmentService {

  private static final Logger log = LoggerFactory.getLogger(ContentEnrichmentService.class);

  private final EmbeddingModel embeddingModel;

  public ContentEnrichmentService(@Autowired(required = false) EmbeddingModel embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  public EnrichedContent enrich(String title, String url, String contentText) {
    float[] vector = null;
    EnrichmentStatus status = EnrichmentStatus.SUCCESS;

    if (embeddingModel != null) {
      try {
        vector = embeddingModel.embed(title + " " + contentText);
      } catch (Exception e) {
        log.warn(
            "Embedding generation failed for URL: {}. Falling back to text-only mode.", url, e);
        status = EnrichmentStatus.FALLBACK;
      }
    } else {
      status = EnrichmentStatus.FALLBACK;
    }

    return new EnrichedContent(
        title,
        url,
        contentText,
        title, // Fallback summary
        List.of(),
        "General",
        1,
        vector,
        status);
  }
}
