package com.contentaggregator.core.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.contentaggregator.annotations.DatabaseTest;
import com.contentaggregator.core.content.ContentFeedRepository;
import com.contentaggregator.core.content.SearchResultItem;
import com.contentaggregator.testutil.containers.KafkaContainersConfig;
import com.contentaggregator.testutil.containers.PostgresContainersConfig;
import com.contentaggregator.testutil.containers.RedisContainersConfig;

@DatabaseTest
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainersConfig.class, KafkaContainersConfig.class, RedisContainersConfig.class})
class HybridSearchIntegrationTest {

  @Autowired private ContentFeedRepository feedRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.execute("TRUNCATE TABLE content_items CASCADE");
  }

  @Test
  @DisplayName("RRF hybrid search: Item matching both dense vector and sparse text ranks highest")
  void shouldRankDualModalityMatchHighestWithRrf() {
    // 1536-dimensional synthetic embedding representation
    float[] targetVector = new float[1536];
    targetVector[0] = 1.0f; // Target direction

    float[] orthogonalVector = new float[1536];
    orthogonalVector[1] = 1.0f; // Orthogonal direction (cosine distance ~ 1.0)

    // Insert Item 1: High semantic similarity (target vector) + Exact Russian keyword match
    UUID idDualMatch = UUID.randomUUID();
    insertContentItem(
        idDualMatch,
        "src-1",
        "ext-1",
        "Разработка на Spring Boot 3 и Java 21",
        "https://example.com/spring-boot-3",
        "Подробное руководство по разработке современных микросервисов на Spring Boot 3 и Java 21",
        targetVector);

    // Insert Item 2: High semantic similarity (target vector), but NO text match
    UUID idVectorOnly = UUID.randomUUID();
    insertContentItem(
        idVectorOnly,
        "src-1",
        "ext-2",
        "Kubernetes orchestration and container management",
        "https://example.com/k8s",
        "Managing large scale clusters with Docker and Kubernetes infrastructure",
        targetVector);

    // Insert Item 3: Low semantic similarity (orthogonal vector), but exact Russian text match
    UUID idTextOnly = UUID.randomUUID();
    insertContentItem(
        idTextOnly,
        "src-1",
        "ext-3",
        "Новости разработки Spring Boot",
        "https://example.com/spring-news",
        "Обзор последних изменений в экосистеме Spring Boot и фреймворке",
        orthogonalVector);

    // Insert Item 4: Irrelevant in both vector and text (embedding is null, text is about pasta)
    UUID idIrrelevant = UUID.randomUUID();
    insertContentItem(
        idIrrelevant,
        "src-1",
        "ext-4",
        "Рецепты итальянской кухни",
        "https://example.com/pasta",
        "Как приготовить традиционную пасту карбонара в домашних условиях",
        null);

    // Execute Hybrid Search for 'Spring Boot' with targetVector
    List<SearchResultItem> results = feedRepository.searchHybrid(targetVector, "Spring Boot", 10);

    assertThat(results).isNotEmpty();

    // The dual match (Item 1) MUST rank first with the highest RRF score
    SearchResultItem topItem = results.get(0);
    assertThat(topItem.contentId()).isEqualTo(idDualMatch);
    assertThat(topItem.title()).contains("Spring Boot 3");

    // Results must contain both vector-only and text-only items ranked above irrelevant item
    List<UUID> resultIds = results.stream().map(SearchResultItem::contentId).toList();
    assertThat(resultIds).contains(idVectorOnly, idTextOnly);
    assertThat(resultIds).doesNotContain(idIrrelevant);

    // Scores must be strictly descending
    for (int i = 0; i < results.size() - 1; i++) {
      assertThat(results.get(i).rrfScore()).isGreaterThanOrEqualTo(results.get(i + 1).rrfScore());
    }
  }

  private void insertContentItem(
      UUID id,
      String sourceId,
      String externalId,
      String title,
      String url,
      String cleanContent,
      float[] vector) {
    String sql =
        """
        INSERT INTO content_items (id, source_id, external_id, title, url, content_text, published_at, read, embedding)
        VALUES (?, ?, ?, ?, ?, ?, ?, true, ?::halfvec)
        """;
    String vectorStr = vector != null ? Arrays.toString(vector) : null;
    jdbcTemplate.update(
        sql, id, sourceId, externalId, title, url, cleanContent, LocalDateTime.now(), vectorStr);
  }
}
