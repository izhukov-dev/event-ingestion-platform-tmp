package com.contentaggregator.ingestion.parser;

public class PayloadTooLargeException extends RuntimeException {
  public PayloadTooLargeException(String message) {
    super(message);
  }
}
