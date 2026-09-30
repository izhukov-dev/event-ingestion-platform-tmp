package com.contentaggregator.ingestion.crawler;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.contentaggregator.annotations.KafkaTest;
import com.contentaggregator.ingestion.security.SsrfValidator;
import com.contentaggregator.testutil.containers.KafkaContainersConfig;
import com.contentaggregator.testutil.containers.PostgresContainersConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

@KafkaTest
@SpringBootTest
@ActiveProfiles("test")
@Import({
  PostgresContainersConfig.class,
  KafkaContainersConfig.class,
  FeedIngestionIntegrationTest.WireMockSsrfTestConfig.class
})
class FeedIngestionIntegrationTest {

  @TestConfiguration
  static class WireMockSsrfTestConfig {
    @Bean
    @Primary
    SsrfValidator testSsrfValidator() {
      return new SsrfValidator() {
        @Override
        public InetAddress validateUrl(String url) {
          return InetAddress.getLoopbackAddress();
        }
      };
    }
  }

  private static WireMockServer wireMockServer;

  @Autowired private FeedIngestionService ingestionService;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void startWireMock() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.execute("DELETE FROM outbox_events WHERE aggregate_type = 'CONTENT'");
  }

  @Test
  @DisplayName(
      "Feed crawler must ingest feed items and handle duplicate polling idempotently via ON CONFLICT")
  void shouldIngestAndDeduplicateFeedItems() {
    String sampleAtomFeed =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Spring Blog</title>
          <entry>
            <id>tag:spring.io,2026:blog-101</id>
            <title>Spring Boot 3.4.3 Released</title>
            <link href="https://spring.io/blog/2026/02/spring-boot-3-4-3"/>
            <updated>2026-02-20T12:00:00Z</updated>
            <summary>Official maintenance release notes</summary>
          </entry>
          <entry>
            <id>tag:spring.io,2026:blog-102</id>
            <title>Spring AI 1.1.8 Vector Support</title>
            <link href="https://spring.io/blog/2026/02/spring-ai-1-1-8"/>
            <updated>2026-02-21T14:00:00Z</updated>
            <summary>pgvector and HNSW integration improvements</summary>
          </entry>
        </feed>
        """;

    wireMockServer.stubFor(
        get(urlEqualTo("/feed.atom"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/atom+xml")
                    .withBody(sampleAtomFeed)));

    IngestionCrawlerProperties.FeedSource source =
        new IngestionCrawlerProperties.FeedSource(
            "test-spring-source", "http://localhost:" + wireMockServer.port() + "/feed.atom");

    // 1. First ingestion cycle: 2 new items
    int ingestedFirstPass = ingestionService.ingestFeed(source);
    assertThat(ingestedFirstPass).isEqualTo(2);

    Integer outboxCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_type = 'CONTENT'", Integer.class);
    assertThat(outboxCount).isEqualTo(2);

    // 2. Second ingestion cycle with identical feed: 0 new items inserted (idempotency)
    int ingestedSecondPass = ingestionService.ingestFeed(source);
    assertThat(ingestedSecondPass).isEqualTo(0);

    Integer outboxCountAfterSecond =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_type = 'CONTENT'", Integer.class);
    assertThat(outboxCountAfterSecond).isEqualTo(2);
  }
}
