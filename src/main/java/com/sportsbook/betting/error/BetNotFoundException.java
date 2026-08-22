package com.sportsbook.betting.error;

public class BetNotFoundException extends RuntimeException {

  public BetNotFoundException(String message) {
    super(message);
  }
}
