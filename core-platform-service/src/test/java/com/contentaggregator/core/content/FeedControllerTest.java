package com.contentaggregator.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.contentaggregator.annotations.UnitTest;
import com.contentaggregator.testutil.TestTags;

@UnitTest
@Tag(TestTags.UNIT)
@Tag(TestTags.FAST)
@ExtendWith(MockitoExtension.class)
@DisplayName("FeedController Unit Tests")
class FeedControllerTest {

  @Mock private FeedService feedService;

  private FeedController feedController;

  @BeforeEach
  void setUp() {
    feedController = new FeedController(feedService);
  }

  @Test
  @DisplayName("getFeed should delegate to FeedService and return FeedItemResponse list")
  void getFeedDelegatesToFeedService() {
    UUID userId = UUID.randomUUID();
    FeedItemResponse response =
        new FeedItemResponse(
            UUID.randomUUID(),
            "src-1",
            "ext-1",
            "Clean Architecture Article",
            "https://example.com/clean-arch",
            "Summary of article",
            LocalDateTime.now());

    when(feedService.getUserFeed(userId, 20)).thenReturn(List.of(response));

    List<FeedItemResponse> result = feedController.getFeed(userId, 20);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).title()).isEqualTo("Clean Architecture Article");
    verify(feedService).getUserFeed(userId, 20);
  }
}
