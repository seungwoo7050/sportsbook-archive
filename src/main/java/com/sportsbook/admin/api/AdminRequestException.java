package com.sportsbook.admin.api;

public final class AdminRequestException extends IllegalArgumentException {

  public AdminRequestException(String message) {
    super(message);
  }

  public AdminRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
