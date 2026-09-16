package com.contentaggregator.core.content;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

  private final FeedService feedService;

  public FeedController(FeedService feedService) {
    this.feedService = feedService;
  }

  @GetMapping
  public List<FeedItemResponse> getFeed(
      @RequestParam("userId") UUID userId,
      @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return feedService.getUserFeed(userId, limit);
  }
}
