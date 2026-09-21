package com.contentaggregator.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.contentaggregator.annotations.UnitTest;

@UnitTest
class JsoupFeedParserTest {

  private final JsoupFeedParser parser = new JsoupFeedParser();

  @Test
  @DisplayName("Parse valid RSS 2.0 with CDATA, unescaping and date parsing")
  void shouldParseStandardRssFeedWithCdata() {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Spring Blog</title>
            <link>https://spring.io/blog</link>
            <item>
              <title><![CDATA[Spring Boot 3.4 SOTA & Loom]]></title>
              <link>https://spring.io/blog/boot34</link>
              <description><![CDATA[Deep dive into <b>Loom</b> &amp; Virtual Threads]]></description>
              <pubDate>Fri, 15 Aug 2025 10:00:00 GMT</pubDate>
              <guid>sb-34-release</guid>
            </item>
          </channel>
        </rss>
        """;

    List<ParsedFeedItem> items =
        parser.parse("source-spring", xml.getBytes(StandardCharsets.UTF_8));

    assertThat(items).hasSize(1);
    ParsedFeedItem item = items.get(0);
    assertThat(item.sourceId()).isEqualTo("source-spring");
    assertThat(item.externalId()).isEqualTo("sb-34-release");
    assertThat(item.title()).isEqualTo("Spring Boot 3.4 SOTA & Loom");
    assertThat(item.url()).isEqualTo("https://spring.io/blog/boot34");
    assertThat(item.contentText()).contains("Deep dive into Loom & Virtual Threads");
    assertThat(item.publishedAt()).isNotNull();
  }

  @Test
  @DisplayName("Parse Atom 1.0 feed with namespaces and href attributes")
  void shouldParseAtomFeedWithNamespaces() {
    String xml =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>JetBrains Blog</title>
          <entry>
            <title>Kotlin 2.1 Announced</title>
            <link rel="alternate" href="https://blog.jetbrains.com/kotlin/2025/11/kotlin-2-1/"/>
            <id>urn:uuid:12345-kotlin</id>
            <updated>2025-11-20T14:30:00Z</updated>
            <summary>Compiler optimizations and K2 mode</summary>
          </entry>
        </feed>
        """;

    List<ParsedFeedItem> items =
        parser.parse("source-jetbrains", xml.getBytes(StandardCharsets.UTF_8));

    assertThat(items).hasSize(1);
    ParsedFeedItem item = items.get(0);
    assertThat(item.externalId()).isEqualTo("urn:uuid:12345-kotlin");
    assertThat(item.title()).isEqualTo("Kotlin 2.1 Announced");
    assertThat(item.url()).isEqualTo("https://blog.jetbrains.com/kotlin/2025/11/kotlin-2-1/");
    assertThat(item.contentText()).isEqualTo("Compiler optimizations and K2 mode");
    assertThat(item.publishedAt()).isNotNull();
  }

  @Test
  @DisplayName("Parse YouTube RSS channel feed with yt:videoId and media:group")
  void shouldParseYouTubeRssFeedWithMediaGroup() {
    String xml =
        """
        <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015"
              xmlns:media="http://search.yahoo.com/mrss/"
              xmlns="http://www.w3.org/2005/Atom">
          <entry>
            <yt:videoId>dQw4w9WgXcQ</yt:videoId>
            <title>Java 21 Virtual Threads in Action</title>
            <link rel="alternate" href="https://www.youtube.com/watch?v=dQw4w9WgXcQ"/>
            <published>2025-06-10T12:00:00+00:00</published>
            <media:group>
              <media:description>A comprehensive masterclass on Project Loom</media:description>
            </media:group>
          </entry>
        </feed>
        """;

    List<ParsedFeedItem> items = parser.parse("source-yt", xml.getBytes(StandardCharsets.UTF_8));

    assertThat(items).hasSize(1);
    ParsedFeedItem item = items.get(0);
    assertThat(item.externalId()).isEqualTo("dQw4w9WgXcQ");
    assertThat(item.title()).isEqualTo("Java 21 Virtual Threads in Action");
    assertThat(item.url()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    assertThat(item.contentText()).contains("A comprehensive masterclass on Project Loom");
  }

  @Test
  @DisplayName("Handle malformed XML with unclosed tags and unescaped entities without crashing")
  void shouldHandleMalformedXmlGracefully() {
    String brokenXml =
        """
        <rss version="2.0"><channel><title>Broken Feed
        <item><title>Item 1 & unescaped <link>https://example.com/1
        <description>No closing tags
        <item><title>Item 2<link>https://example.com/2<guid>g2
        """;

    List<ParsedFeedItem> items =
        parser.parse("source-broken", brokenXml.getBytes(StandardCharsets.UTF_8));

    assertThat(items).isNotEmpty();
  }

  @Test
  @DisplayName("Reject payload exceeding 5MB limit with PayloadTooLargeException")
  void shouldRejectPayloadExceedingSizeLimit() {
    byte[] oversizedPayload = new byte[6 * 1024 * 1024]; // 6 MB

    assertThatThrownBy(() -> parser.parse("source-huge", oversizedPayload))
        .isInstanceOf(PayloadTooLargeException.class);
  }
}
