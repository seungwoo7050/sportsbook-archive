package com.sportsbook.oddsfeed.publisher;

public class KafkaPublishException extends RuntimeException {

  public KafkaPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
