package com.contentaggregator.core.content;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.contentaggregator.core.cache.ProbabilisticCacheService;
import com.contentaggregator.core.user.UserFeedVersionService;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class FeedServiceImpl implements FeedService {

  private static final Duration FEED_CACHE_TTL = Duration.ofMinutes(5);
  private static final Duration FEED_COMPUTATION_DELTA = Duration.ofMillis(20);
  private static final TypeReference<List<FeedItemResponse>> FEED_RESPONSE_TYPE =
      new TypeReference<>() {};

  private final ContentFeedRepository feedRepository;
  private final ProbabilisticCacheService cacheService;
  private final UserFeedVersionService userFeedVersionService;

  public FeedServiceImpl(
      ContentFeedRepository feedRepository,
      ProbabilisticCacheService cacheService,
      UserFeedVersionService userFeedVersionService) {
    this.feedRepository = feedRepository;
    this.cacheService = cacheService;
    this.userFeedVersionService = userFeedVersionService;
  }

  @Override
  public List<FeedItemResponse> getUserFeed(UUID userId, int limit) {
    long version = userFeedVersionService.getVersion(userId);
    String cacheKey = "feed:" + userId + ":v" + version + ":l" + limit;

    return cacheService.getOrCompute(
        cacheKey,
        FEED_RESPONSE_TYPE,
        FEED_CACHE_TTL,
        FEED_COMPUTATION_DELTA,
        () ->
            feedRepository.findUnreadFeed(limit).stream()
                .map(FeedItemResponse::fromDomain)
                .toList());
  }
}
