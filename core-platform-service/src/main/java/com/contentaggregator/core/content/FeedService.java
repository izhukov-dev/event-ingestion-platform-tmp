package com.contentaggregator.core.content;

import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface FeedService {
  List<FeedItemResponse> getUserFeed(UUID userId, int limit);
}
