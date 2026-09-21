package com.contentaggregator.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.contentaggregator.annotations.UnitTest;
import com.contentaggregator.core.cache.ProbabilisticCacheService;
import com.contentaggregator.core.user.UserFeedVersionService;
import com.contentaggregator.testutil.TestTags;
import com.fasterxml.jackson.core.type.TypeReference;

@UnitTest
@Tag(TestTags.UNIT)
@Tag(TestTags.FAST)
@ExtendWith(MockitoExtension.class)
@DisplayName("FeedServiceImpl Unit Tests")
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
  @DisplayName("getUserFeed should request feed from cache using user versioned key and map to DTO")
  @SuppressWarnings("unchecked")
  void getUserFeedUsesProbabilisticCacheWithUserVersionKey() {
    UUID userId = UUID.randomUUID();
    when(userFeedVersionService.getVersion(userId)).thenReturn(3L);

    FeedItemResponse dto =
        new FeedItemResponse(
            UUID.randomUUID(),
            "source-1",
            "ext-1",
            "Test Title",
            "https://example.com/post",
            "Clean content snippet",
            LocalDateTime.now());

    when(cacheService.getOrCompute(
            eq("feed:" + userId + ":v3:l20"),
            any(TypeReference.class),
            eq(Duration.ofMinutes(5)),
            eq(Duration.ofMillis(20)),
            any(Supplier.class)))
        .thenReturn(List.of(dto));

    List<FeedItemResponse> result = feedService.getUserFeed(userId, 20);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).title()).isEqualTo("Test Title");
    verify(userFeedVersionService).getVersion(userId);
    verify(cacheService)
        .getOrCompute(
            eq("feed:" + userId + ":v3:l20"),
            any(TypeReference.class),
            eq(Duration.ofMinutes(5)),
            eq(Duration.ofMillis(20)),
            any(Supplier.class));
  }
}
