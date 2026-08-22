package com.sportsbook.betting.error;

import com.sportsbook.protocol.error.ErrorCode;

public class ValidationFailedException extends BetPlacementException {

  public ValidationFailedException(String message) {
    super(ErrorCode.VALIDATION_FAILED, message);
  }
}
