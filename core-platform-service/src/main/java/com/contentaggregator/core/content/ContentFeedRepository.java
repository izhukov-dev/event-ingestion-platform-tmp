package com.contentaggregator.core.content;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ContentFeedRepository {

  private static final String HYBRID_SEARCH_SQL =
      """
      WITH vector_candidates AS (
          SELECT id, ROW_NUMBER() OVER (ORDER BY embedding <=> ?::halfvec ASC) as rank
          FROM content_items
          WHERE embedding IS NOT NULL
          ORDER BY embedding <=> ?::halfvec ASC
          LIMIT 50
      ),
      query_term AS (
          SELECT ?::text AS q
      ),
      text_candidates AS (
          SELECT id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(search_vector, (websearch_to_tsquery('russian', q.q) || websearch_to_tsquery('english', q.q))) DESC) as rank
          FROM content_items, query_term q
          WHERE search_vector @@ (websearch_to_tsquery('russian', q.q) || websearch_to_tsquery('english', q.q))
          ORDER BY ts_rank_cd(search_vector, (websearch_to_tsquery('russian', q.q) || websearch_to_tsquery('english', q.q))) DESC
          LIMIT 50
      )
      SELECT
          COALESCE(v.id, t.id) AS content_id,
          c.title,
          c.url,
          c.content_text AS clean_content,
          (COALESCE(1.0 / (60 + v.rank), 0.0) + COALESCE(1.0 / (60 + t.rank), 0.0)) AS rrf_score
      FROM vector_candidates v
      FULL OUTER JOIN text_candidates t ON v.id = t.id
      JOIN content_items c ON c.id = COALESCE(v.id, t.id)
      ORDER BY rrf_score DESC
      LIMIT ?
      """;

  private static final String UNREAD_FEED_SQL =
      """
      SELECT id, source_id, external_id, title, url, content_text AS clean_content, published_at
      FROM content_items
      WHERE read = FALSE
      ORDER BY published_at DESC
      LIMIT ?
      """;

  private final JdbcTemplate jdbcTemplate;

  public ContentFeedRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<SearchResultItem> searchHybrid(float[] queryVector, String textQuery, int limit) {
    String vectorStr = Arrays.toString(queryVector);
    return jdbcTemplate.query(
        HYBRID_SEARCH_SQL, new SearchResultRowMapper(), vectorStr, vectorStr, textQuery, limit);
  }

  public List<ContentItem> findUnreadFeed(int limit) {
    return jdbcTemplate.query(UNREAD_FEED_SQL, new ContentItemRowMapper(), limit);
  }

  private static class SearchResultRowMapper implements RowMapper<SearchResultItem> {
    @Override
    public SearchResultItem mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new SearchResultItem(
          rs.getObject("content_id", UUID.class),
          rs.getString("title"),
          rs.getString("url"),
          rs.getString("clean_content"),
          rs.getDouble("rrf_score"));
    }
  }

  private static class ContentItemRowMapper implements RowMapper<ContentItem> {
    @Override
    public ContentItem mapRow(ResultSet rs, int rowNum) throws SQLException {
      OffsetDateTime publishedAt = rs.getObject("published_at", OffsetDateTime.class);
      return new ContentItem(
          rs.getObject("id", UUID.class),
          rs.getString("source_id"),
          rs.getString("external_id"),
          rs.getString("title"),
          rs.getString("url"),
          rs.getString("clean_content"),
          publishedAt != null ? publishedAt.toLocalDateTime() : null);
    }
  }
}
