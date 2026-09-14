package com.contentaggregator.ingestion.crawler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FeedIngestionScheduler {

  private static final Logger log = LoggerFactory.getLogger(FeedIngestionScheduler.class);

  private final FeedIngestionService ingestionService;
  private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public FeedIngestionScheduler(FeedIngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  @Scheduled(initialDelay = 5000, fixedDelayString = "${ingestion.crawler.poll-interval:60000}")
  public void pollFeeds() {
    virtualThreadExecutor.submit(
        () -> {
          try {
            int ingested = ingestionService.ingestAllFeeds();
            if (ingested > 0) {
              log.info("Feed crawler cycle finished. Ingested {} new publications.", ingested);
            }
          } catch (Exception e) {
            log.error("Unhandled exception during feed crawling cycle: {}", e.getMessage(), e);
          }
        });
  }
}
