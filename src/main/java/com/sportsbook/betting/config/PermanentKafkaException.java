package com.sportsbook.betting.config;

public final class PermanentKafkaException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PermanentKafkaException(String message) {
    super(message);
  }

  public PermanentKafkaException(String message, Throwable cause) {
    super(message, cause);
  }
}
