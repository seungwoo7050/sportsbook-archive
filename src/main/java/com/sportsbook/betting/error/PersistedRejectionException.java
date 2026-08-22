package com.sportsbook.betting.error;

import com.sportsbook.protocol.error.ErrorCode;

public class PersistedRejectionException extends BetPlacementException {

  public PersistedRejectionException(ErrorCode errorCode, String detail) {
    super(errorCode, detail);
  }
}
