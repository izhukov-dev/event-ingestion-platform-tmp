package com.contentaggregator.ingestion.parser;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class JsoupFeedParser {

  private static final int MAX_PAYLOAD_BYTES = 5 * 1024 * 1024; // 5 MB

  public List<ParsedFeedItem> parse(String sourceId, byte[] payload) {
    if (payload.length > MAX_PAYLOAD_BYTES) {
      throw new PayloadTooLargeException(
          "Payload size " + payload.length + " exceeds maximum allowed 5MB");
    }

    Document doc = Jsoup.parse(new String(payload, StandardCharsets.UTF_8), "", Parser.xmlParser());

    Elements rssItems = doc.select("item");
    if (!rssItems.isEmpty()) {
      return parseRssItems(sourceId, rssItems);
    }

    Elements atomEntries = doc.select("entry");
    if (!atomEntries.isEmpty()) {
      return parseAtomEntries(sourceId, atomEntries);
    }

    return List.of();
  }

  private List<ParsedFeedItem> parseRssItems(String sourceId, Elements rssItems) {
    List<ParsedFeedItem> items = new ArrayList<>();
    for (Element item : rssItems) {
      String title = getElementText(item, "title");
      String link = getElementText(item, "link");
      String description = getElementText(item, "description");
      String guid = getElementText(item, "guid");
      String pubDateStr = getElementText(item, "pubDate");

      String externalId = (guid != null && !guid.isBlank()) ? guid : link;
      LocalDateTime publishedAt = parseDate(pubDateStr);

      items.add(
          new ParsedFeedItem(
              sourceId,
              externalId != null ? externalId : link,
              cleanHtml(title),
              link,
              cleanHtml(description),
              publishedAt != null ? publishedAt : LocalDateTime.now()));
    }
    return items;
  }

  private List<ParsedFeedItem> parseAtomEntries(String sourceId, Elements atomEntries) {
    List<ParsedFeedItem> items = new ArrayList<>();
    for (Element entry : atomEntries) {
      String title = getElementText(entry, "title");
      String externalId = extractAtomId(entry);
      String link = extractAtomLink(entry);
      String content = extractAtomContent(entry);
      LocalDateTime publishedAt = extractAtomPublishedDate(entry);

      items.add(
          new ParsedFeedItem(
              sourceId,
              externalId != null ? externalId : link,
              cleanHtml(title),
              link,
              cleanHtml(content),
              publishedAt != null ? publishedAt : LocalDateTime.now()));
    }
    return items;
  }

  private String extractAtomId(Element entry) {
    Element ytVideoId = entry.selectFirst("yt|videoId");
    return (ytVideoId != null) ? ytVideoId.text().trim() : getElementText(entry, "id");
  }

  private String extractAtomLink(Element entry) {
    Element linkElem = entry.selectFirst("link[href]");
    return (linkElem != null) ? linkElem.attr("href") : getElementText(entry, "link");
  }

  private String extractAtomContent(Element entry) {
    Element mediaDesc = entry.selectFirst("media|group media|description");
    String content = (mediaDesc != null) ? mediaDesc.text() : null;
    if (content == null || content.isBlank()) {
      content = getElementText(entry, "summary");
    }
    if (content == null || content.isBlank()) {
      content = getElementText(entry, "content");
    }
    return content;
  }

  private LocalDateTime extractAtomPublishedDate(Element entry) {
    String publishedStr = getElementText(entry, "published");
    if (publishedStr == null || publishedStr.isBlank()) {
      publishedStr = getElementText(entry, "updated");
    }
    return parseDate(publishedStr);
  }

  private String getElementText(Element parent, String cssQuery) {
    Element elem = parent.selectFirst(cssQuery);
    return elem != null ? elem.text().trim() : null;
  }

  private String cleanHtml(String text) {
    if (text == null) {
      return "";
    }
    return Jsoup.parse(text).text().trim();
  }

  private LocalDateTime parseDate(String dateStr) {
    if (dateStr == null || dateStr.isBlank()) {
      return null;
    }
    try {
      return ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime();
    } catch (Exception ignored) {
      // Try next format
    }

    try {
      return ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime();
    } catch (Exception ignored) {
      // Try next format
    }

    try {
      return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    } catch (Exception ignored) {
      // Fallback to null
    }

    return null;
  }
}
