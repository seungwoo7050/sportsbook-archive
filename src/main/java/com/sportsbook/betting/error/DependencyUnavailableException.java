package com.sportsbook.betting.error;

import com.sportsbook.protocol.error.ErrorCode;

public class DependencyUnavailableException extends BetPlacementException {

  public DependencyUnavailableException(String message) {
    super(ErrorCode.SERVICE_UNAVAILABLE, message);
  }

  public DependencyUnavailableException(String message, Throwable cause) {
    super(ErrorCode.SERVICE_UNAVAILABLE, message);
    initCause(cause);
  }
}
