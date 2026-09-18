package com.contentaggregator.core.content;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.contentaggregator.core.cache.ProbabilisticCacheService;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class ContentDiscoveryService {

  private static final Logger log = LoggerFactory.getLogger(ContentDiscoveryService.class);
  private static final Duration SEARCH_CACHE_TTL = Duration.ofSeconds(60);
  private static final Duration SEARCH_COMPUTE_DELTA = Duration.ofMillis(50);
  private static final int VECTOR_DIMENSION = 1536;
  private static final TypeReference<List<SearchResultItem>> SEARCH_RESPONSE_TYPE =
      new TypeReference<>() {};

  private final ContentFeedRepository feedRepository;
  private final ProbabilisticCacheService cacheService;
  private final Optional<EmbeddingModel> embeddingModel;

  public ContentDiscoveryService(
      ContentFeedRepository feedRepository,
      ProbabilisticCacheService cacheService,
      @Autowired(required = false) EmbeddingModel embeddingModel) {
    this.feedRepository = feedRepository;
    this.cacheService = cacheService;
    this.embeddingModel = Optional.ofNullable(embeddingModel);
  }

  public List<SearchResultItem> search(String query, int limit) {
    String cacheKey = "search:" + query.trim().toLowerCase() + ":" + limit;

    return cacheService.getOrCompute(
        cacheKey,
        SEARCH_RESPONSE_TYPE,
        SEARCH_CACHE_TTL,
        SEARCH_COMPUTE_DELTA,
        () -> {
          float[] vector = computeVectorWithFallback(query);
          return feedRepository.searchHybrid(vector, query, limit);
        });
  }

  private float[] computeVectorWithFallback(String query) {
    if (embeddingModel.isPresent()) {
      try {
        return embeddingModel.get().embed(query);
      } catch (Exception e) {
        log.warn(
            "Embedding generation failed for query '{}'. Graceful fallback to deterministic vector.",
            query,
            e);
      }
    }
    return DeterministicVectorProvider.generate(query, VECTOR_DIMENSION);
  }
}
