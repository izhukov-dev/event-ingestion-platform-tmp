package com.contentaggregator.ingestion.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.contentaggregator.annotations.UnitTest;

@UnitTest
class SsrfValidatorTest {

  private final SsrfValidator validator = new SsrfValidator();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://spring.io/blog.atom",
        "https://www.youtube.com/feeds/videos.xml?channel_id=UC123",
        "https://github.com/torvalds/linux/releases.atom",
        "http://sub.domain.example.com/rss"
      })
  @DisplayName("Valid public URLs must pass SSRF validation")
  void shouldAllowPublicUrls(String url) {
    assertThat(validator.isSafeUrl(url)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://127.0.0.1:8080/feed",
        "http://localhost/rss",
        "http://10.0.1.5/api",
        "http://172.16.0.1/admin",
        "http://192.168.1.100/status",
        "http://169.254.169.254/latest/meta-data/",
        "http://[::1]/metrics",
        "ftp://example.com/feed.xml",
        "file:///etc/passwd"
      })
  @DisplayName("Private IPs, metadata endpoints and non-HTTP protocols must be blocked")
  void shouldBlockPrivateAndSuspiciousUrls(String url) {
    assertThat(validator.isSafeUrl(url)).isFalse();
    assertThatThrownBy(() -> validator.validateUrl(url)).isInstanceOf(SsrfBlockedException.class);
  }
}
