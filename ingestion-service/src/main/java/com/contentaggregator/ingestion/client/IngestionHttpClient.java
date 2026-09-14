package com.contentaggregator.ingestion.client;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.contentaggregator.ingestion.security.SsrfBlockedException;
import com.contentaggregator.ingestion.security.SsrfValidator;

@Component
public class IngestionHttpClient {

  private static final int HTTP_MULTIPLE_CHOICES = 300;
  private static final int HTTP_BAD_REQUEST = 400;
  private static final int DEFAULT_HTTP_PORT = 80;
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
  private static final String ACCEPT =
      "text/html,application/xhtml+xml,application/xml;q=0.9,application/rss+xml;q=0.8,*/*;q=0.7";

  private final HttpClient httpClient;
  private final SsrfValidator ssrfValidator;

  public IngestionHttpClient() {
    this(new SsrfValidator());
  }

  @Autowired
  public IngestionHttpClient(SsrfValidator ssrfValidator) {
    this(
        ssrfValidator,
        HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  IngestionHttpClient(SsrfValidator ssrfValidator, HttpClient httpClient) {
    this.ssrfValidator = ssrfValidator;
    this.httpClient = httpClient;
  }

  public byte[] fetch(String url) {
    InetAddress resolvedAddress = ssrfValidator.validateUrl(url);

    try {
      HttpRequest request = buildHttpRequest(url, resolvedAddress);
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      validateResponseStatus(response.statusCode(), url);
      return response.body();
    } catch (FeedFetchException | SsrfBlockedException e) {
      throw e;
    } catch (Exception e) {
      throw new FeedFetchException("Failed to fetch feed from " + url, e);
    }
  }

  private HttpRequest buildHttpRequest(String url, InetAddress resolvedAddress) {
    URI originalUri = URI.create(url);
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", ACCEPT)
            .timeout(Duration.ofSeconds(15))
            .GET();

    if ("http".equalsIgnoreCase(originalUri.getScheme()) && resolvedAddress != null) {
      requestBuilder
          .uri(createPinnedHttpUri(originalUri, resolvedAddress))
          .header("Host", originalUri.getHost());
    } else {
      requestBuilder.uri(originalUri);
    }
    return requestBuilder.build();
  }

  private URI createPinnedHttpUri(URI originalUri, InetAddress resolvedAddress) {
    int port = originalUri.getPort() != -1 ? originalUri.getPort() : DEFAULT_HTTP_PORT;
    String rawPath = originalUri.getRawPath();
    if (rawPath == null || rawPath.isEmpty()) {
      rawPath = "/";
    }
    if (originalUri.getRawQuery() != null) {
      rawPath += "?" + originalUri.getRawQuery();
    }
    return URI.create("http://" + resolvedAddress.getHostAddress() + ":" + port + rawPath);
  }

  private void validateResponseStatus(int statusCode, String url) {
    if (statusCode >= HTTP_MULTIPLE_CHOICES && statusCode < HTTP_BAD_REQUEST) {
      throw new FeedFetchException(
          "HTTP redirection blocked by SSRF policy (HTTP " + statusCode + ") for " + url);
    }
    if (statusCode >= HTTP_BAD_REQUEST) {
      throw new FeedFetchException("HTTP error " + statusCode + " fetching " + url);
    }
  }
}
