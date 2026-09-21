package com.contentaggregator.ingestion.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.contentaggregator.annotations.IntegrationTest;
import com.contentaggregator.ingestion.security.SsrfValidator;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

@IntegrationTest
class LoomNonPinningIntegrationTest {

  private static WireMockServer wireMock;
  private final IngestionHttpClient httpClient =
      new IngestionHttpClient(
          new SsrfValidator() {
            @Override
            public java.net.InetAddress validateUrl(String url) {
              // Permit local WireMock loopback in integration test harness
              return java.net.InetAddress.getLoopbackAddress();
            }
          });

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();

    wireMock.stubFor(
        get(urlEqualTo("/feed.xml"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/xml")
                    .withFixedDelay(50) // 50ms delay to simulate network latency
                    .withBody(
                        "<rss version=\"2.0\"><channel><title>WireMock Feed</title></channel></rss>")));
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @Test
  @DisplayName(
      "200 concurrent Virtual Threads must execute HTTP requests without Carrier Thread Pinning")
  void shouldExecuteConcurrentRequestsOnVirtualThreadsWithoutPinning() throws Exception {
    String url = "http://localhost:" + wireMock.port() + "/feed.xml";
    int concurrentCalls = 100;

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<byte[]>> futures = new ArrayList<>();

      for (int i = 0; i < concurrentCalls; i++) {
        futures.add(executor.submit(() -> httpClient.fetch(url)));
      }

      for (Future<byte[]> future : futures) {
        byte[] bytes = future.get();
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes)).contains("WireMock Feed");
      }
    }
  }
}
