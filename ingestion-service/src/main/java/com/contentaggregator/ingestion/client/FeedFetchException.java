package com.contentaggregator.ingestion.client;

public class FeedFetchException extends RuntimeException {

  public FeedFetchException(String message) {
    super(message);
  }

  public FeedFetchException(String message, Throwable cause) {
    super(message, cause);
  }
}
