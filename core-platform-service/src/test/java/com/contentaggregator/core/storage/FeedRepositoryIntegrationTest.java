package com.contentaggregator.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.contentaggregator.core.content.ContentItem;
import com.contentaggregator.testutil.containers.KafkaContainersConfig;
import com.contentaggregator.testutil.containers.PostgresContainersConfig;
import com.contentaggregator.testutil.containers.RedisContainersConfig;

@DatabaseTest
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainersConfig.class, KafkaContainersConfig.class, RedisContainersConfig.class})
class FeedRepositoryIntegrationTest {

  @Autowired private ContentFeedRepository feedRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.execute("TRUNCATE TABLE content_items CASCADE");
  }

  @Test
  @DisplayName(
      "findUnreadFeed must query content_items with read = FALSE against real Postgres schema")
  void shouldFetchUnreadFeedSuccessfully() {
    UUID unreadId = UUID.randomUUID();
    UUID readId = UUID.randomUUID();

    // 1. Insert unread item (read = false)
    jdbcTemplate.update(
        "INSERT INTO content_items (id, source_id, external_id, title, url, content_text, published_at, read) "
            + "VALUES (?, 'src-unread', 'ext-unread-1', 'Unread Post', 'https://example.com/unread', 'Content unread', now(), false)",
        unreadId);

    // 2. Insert read item (read = true)
    jdbcTemplate.update(
        "INSERT INTO content_items (id, source_id, external_id, title, url, content_text, published_at, read) "
            + "VALUES (?, 'src-read', 'ext-read-1', 'Read Post', 'https://example.com/read', 'Content read', now(), true)",
        readId);

    // 3. Execute findUnreadFeed
    List<ContentItem> items = feedRepository.findUnreadFeed(10);

    // 4. Assert only unread item is returned
    assertThat(items).hasSize(1);
    ContentItem item = items.get(0);
    assertThat(item.id()).isEqualTo(unreadId);
    assertThat(item.title()).isEqualTo("Unread Post");
  }
}
