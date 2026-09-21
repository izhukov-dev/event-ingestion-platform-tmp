package com.contentaggregator.core.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import com.contentaggregator.annotations.UnitTest;

@UnitTest
class AiEnrichmentFallbackTest {

  @Test
  @DisplayName(
      "When AI service is unavailable, enrichment returns fallback record with null embedding and raw text intact")
  void shouldFallbackGracefullyWhenAiServiceThrowsException() {
    EmbeddingModel failingEmbeddingModel = mock(EmbeddingModel.class);
    when(failingEmbeddingModel.embed(anyString()))
        .thenThrow(new RuntimeException("LLM upstream 503 Service Unavailable"));

    ContentEnrichmentService service = new ContentEnrichmentService(failingEmbeddingModel);

    EnrichedContent result =
        service.enrich(
            "Spring Boot 3 Enterprise",
            "https://example.com/spring",
            "Руководство по Spring AI и векторному поиску");

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(EnrichmentStatus.FALLBACK);
    assertThat(result.embedding()).isNull();
    assertThat(result.contentText()).contains("Руководство по Spring AI");
  }
}
