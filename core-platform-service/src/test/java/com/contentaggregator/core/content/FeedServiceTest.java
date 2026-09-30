package com.contentaggregator.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.contentaggregator.annotations.UnitTest;
import com.contentaggregator.core.cache.ProbabilisticCacheService;
import com.contentaggregator.core.user.UserFeedVersionService;
import com.fasterxml.jackson.core.type.TypeReference;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

  @Mock private ContentFeedRepository feedRepository;
  @Mock private ProbabilisticCacheService cacheService;
  @Mock private UserFeedVersionService userFeedVersionService;

  private FeedServiceImpl feedService;

  @BeforeEach
  void setUp() {
    feedService = new FeedServiceImpl(feedRepository, cacheService, userFeedVersionService);
  }

  @Test
  @DisplayName("getUserFeed formats versioned cache key feed:{userId}:v{ver}:l{limit}")
  @SuppressWarnings("unchecked")
  void shouldBuildCorrectCacheKey() {
    UUID userId = UUID.randomUUID();
    given(userFeedVersionService.getVersion(userId)).willReturn(5L);

    feedService.getUserFeed(userId, 50);

    verify(cacheService)
        .getOrCompute(
            org.mockito.ArgumentMatchers.eq("feed:" + userId + ":v5:l50"),
            any(TypeReference.class),
            any(),
            any(),
            any(Supplier.class));
  }

  @Test
  @DisplayName(
      "When cache executes computation loader, repository queries unread feed and maps to DTO")
  @SuppressWarnings("unchecked")
  void shouldDelegateToRepositoryWhenLoaderInvoked() {
    UUID userId = UUID.randomUUID();
    given(userFeedVersionService.getVersion(userId)).willReturn(1L);

    ContentItem dbItem =
        new ContentItem(
            UUID.randomUUID(),
            "src-db",
            "ext-db",
            "Database Fallback Post",
            "https://example.com/db",
            "Fallback clean content",
            LocalDateTime.now());
    given(feedRepository.findUnreadFeed(20)).willReturn(List.of(dbItem));

    given(
            cacheService.getOrCompute(
                anyString(), any(TypeReference.class), any(), any(), any(Supplier.class)))
        .willAnswer(
            invocation -> {
              Supplier<List<FeedItemResponse>> supplier = invocation.getArgument(4);
              return supplier.get();
            });

    List<FeedItemResponse> result = feedService.getUserFeed(userId, 20);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).title()).isEqualTo("Database Fallback Post");
    verify(feedRepository).findUnreadFeed(20);
  }
}
