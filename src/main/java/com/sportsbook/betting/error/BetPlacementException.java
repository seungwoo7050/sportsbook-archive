package com.sportsbook.betting.error;

import com.sportsbook.protocol.error.ErrorCode;
import java.util.Objects;

public abstract class BetPlacementException extends RuntimeException {

  private final transient ErrorCode errorCode;

  protected BetPlacementException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
  }

  public ErrorCode errorCode() {
    return errorCode;
  }
}
