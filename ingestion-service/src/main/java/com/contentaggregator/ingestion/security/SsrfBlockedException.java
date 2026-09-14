package com.contentaggregator.ingestion.security;

public class SsrfBlockedException extends RuntimeException {
  public SsrfBlockedException(String message) {
    super(message);
  }

  public SsrfBlockedException(String message, Throwable cause) {
    super(message, cause);
  }
}
