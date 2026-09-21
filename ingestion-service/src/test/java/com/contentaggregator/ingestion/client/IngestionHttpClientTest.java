package com.contentaggregator.ingestion.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.contentaggregator.ingestion.security.SsrfBlockedException;
import com.contentaggregator.ingestion.security.SsrfValidator;
import com.contentaggregator.testutil.TestTags;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

@Tag(TestTags.UNIT)
@Tag(TestTags.FAST)
@DisplayName("IngestionHttpClient Unit Tests")
class IngestionHttpClientTest {

  private static WireMockServer wireMock;

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();

    wireMock.stubFor(
        get(urlEqualTo("/ok.xml"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/xml")
                    .withBody("<rss><channel><title>Valid Feed</title></channel></rss>")));

    wireMock.stubFor(
        get(urlEqualTo("/redirect"))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader("Location", "http://169.254.169.254/latest/meta-data/")));

    wireMock.stubFor(
        get(urlEqualTo("/server-error"))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @Test
  @DisplayName(
      "Should throw SsrfBlockedException and abort HTTP call when SsrfValidator rejects URL")
  void shouldThrowSsrfBlockedExceptionWhenValidatorFails() {
    SsrfValidator validator = mock(SsrfValidator.class);
    String targetUrl = "http://127.0.0.1:8080/feed";
    doThrow(new SsrfBlockedException("Loopback IP address blocked"))
        .when(validator)
        .validateUrl(targetUrl);

    IngestionHttpClient client = new IngestionHttpClient(validator);

    assertThatThrownBy(() -> client.fetch(targetUrl))
        .isInstanceOf(SsrfBlockedException.class)
        .hasMessageContaining("Loopback IP address blocked");
  }

  @Test
  @DisplayName("Should block HTTP redirects with HttpClient.Redirect.NEVER policy")
  void shouldBlockRedirectionWhenServerReturns302() {
    SsrfValidator permissiveValidator = mock(SsrfValidator.class);
    IngestionHttpClient client = new IngestionHttpClient(permissiveValidator);
    String redirectUrl = "http://localhost:" + wireMock.port() + "/redirect";

    assertThatThrownBy(() -> client.fetch(redirectUrl))
        .isInstanceOf(FeedFetchException.class)
        .hasMessageContaining("HTTP redirection blocked by SSRF policy (HTTP 302)");
  }

  @Test
  @DisplayName("Should return byte array when target returns HTTP 200")
  void shouldReturnPayloadWhenResponseIs200() {
    SsrfValidator permissiveValidator = mock(SsrfValidator.class);
    IngestionHttpClient client = new IngestionHttpClient(permissiveValidator);
    String okUrl = "http://localhost:" + wireMock.port() + "/ok.xml";

    byte[] body = client.fetch(okUrl);

    assertThat(body).isNotEmpty();
    assertThat(new String(body)).contains("Valid Feed");
  }

  @Test
  @DisplayName("Should throw FeedFetchException when server returns HTTP 500")
  void shouldThrowFeedFetchExceptionWhenServerReturns500() {
    SsrfValidator permissiveValidator = mock(SsrfValidator.class);
    IngestionHttpClient client = new IngestionHttpClient(permissiveValidator);
    String errorUrl = "http://localhost:" + wireMock.port() + "/server-error";

    assertThatThrownBy(() -> client.fetch(errorUrl))
        .isInstanceOf(FeedFetchException.class)
        .hasMessageContaining("HTTP error 500 fetching");
  }
}
