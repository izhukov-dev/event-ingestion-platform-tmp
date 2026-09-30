package com.contentaggregator.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.contentaggregator.annotations.DatabaseTest;
import com.contentaggregator.testutil.containers.KafkaContainersConfig;
import com.contentaggregator.testutil.containers.PostgresContainersConfig;
import com.contentaggregator.testutil.containers.RedisContainersConfig;

@DatabaseTest
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainersConfig.class, KafkaContainersConfig.class, RedisContainersConfig.class})
class DatabaseMigrationIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("1. PostgreSQL extension 'vector' must be installed and active")
  void shouldHaveVectorExtensionInstalled() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName("2. Table 'content_items' must exist with halfvec, tsvector and proper schema")
  void shouldHaveContentItemsTableWithCorrectSchema() {
    Integer tableExists =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'content_items'",
            Integer.class);
    assertThat(tableExists).isEqualTo(1);

    // Verify embedding column data type is halfvec
    String embeddingType =
        jdbcTemplate.queryForObject(
            "SELECT udt_name FROM information_schema.columns WHERE table_name = 'content_items' AND column_name = 'embedding'",
            String.class);
    assertThat(embeddingType).isEqualTo("halfvec");

    // Verify search_vector column data type is tsvector
    String searchVectorType =
        jdbcTemplate.queryForObject(
            "SELECT udt_name FROM information_schema.columns WHERE table_name = 'content_items' AND column_name = 'search_vector'",
            String.class);
    assertThat(searchVectorType).isEqualTo("tsvector");
  }

  @Test
  @DisplayName("3. HNSW index with halfvec_cosine_ops must exist on content_items.embedding")
  void shouldHaveHnswCosineIndexOnEmbedding() {
    List<String> indexDefs =
        jdbcTemplate.queryForList(
            "SELECT indexdef FROM pg_indexes WHERE tablename = 'content_items' AND indexname = 'idx_content_items_hnsw_embedding'",
            String.class);
    assertThat(indexDefs).hasSize(1);
    String def = indexDefs.get(0).toLowerCase();
    assertThat(def).contains("using hnsw");
    assertThat(def).contains("halfvec_cosine_ops");
  }

  @Test
  @DisplayName("4. Partial index idx_content_items_unread_feed must exist for unread items")
  void shouldHavePartialIndexForUnreadFeed() {
    List<String> indexDefs =
        jdbcTemplate.queryForList(
            "SELECT indexdef FROM pg_indexes WHERE tablename = 'content_items' AND indexname = 'idx_content_items_unread_feed'",
            String.class);
    assertThat(indexDefs).hasSize(1);
    String def = indexDefs.get(0).toLowerCase();
    assertThat(def).contains("read = false");
  }

  @Test
  @DisplayName("5. Table 'outbox_events' must exist with unprocessed partial index")
  void shouldHaveOutboxEventsTableAndIndex() {
    Integer tableExists =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'outbox_events'",
            Integer.class);
    assertThat(tableExists).isEqualTo(1);

    List<String> indexDefs =
        jdbcTemplate.queryForList(
            "SELECT indexdef FROM pg_indexes WHERE tablename = 'outbox_events' AND indexname = 'idx_outbox_events_unprocessed'",
            String.class);
    assertThat(indexDefs).hasSize(1);
    String def = indexDefs.get(0).toLowerCase();
    assertThat(def).contains("processed_at is null");
  }

  @Test
  @DisplayName(
      "6. halfvec(1536) insertion, distance calculation and tsvector generation must function end-to-end")
  void shouldSupportHalfvecAndTsvectorEndToEnd() {
    // Clean test row
    jdbcTemplate.update("DELETE FROM content_items WHERE external_id = 'test-ext-101'");

    // Insert item with 1536-dimensional halfvec vector
    jdbcTemplate.update(
        "INSERT INTO content_items ("
            + "  id, source_id, external_id, title, url, content_text, embedding, read, published_at, created_at"
            + ") VALUES ("
            + "  gen_random_uuid(), 'src-test', 'test-ext-101', 'Postgres Vector Architecture', "
            + "  'https://example.com/post', 'High performance search with halfvec and HNSW indexing', "
            + "  array_fill(0.05, ARRAY[1536])::halfvec, false, now(), now()"
            + ")");

    // Verify distance calculation with halfvec <=> operator
    Double distance =
        jdbcTemplate.queryForObject(
            "SELECT embedding <=> array_fill(0.05, ARRAY[1536])::halfvec FROM content_items WHERE external_id = 'test-ext-101'",
            Double.class);
    assertThat(distance).isNotNull();
    assertThat(distance).isLessThan(0.0001);

    // Verify search_vector generated column contains lexemes
    String searchVector =
        jdbcTemplate.queryForObject(
            "SELECT search_vector::text FROM content_items WHERE external_id = 'test-ext-101'",
            String.class);
    assertThat(searchVector).isNotNull();
    assertThat(searchVector).contains("'vector'");
    assertThat(searchVector).contains("'architectur'");
  }
}
