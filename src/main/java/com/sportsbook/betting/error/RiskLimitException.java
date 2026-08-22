package com.sportsbook.betting.error;

import com.sportsbook.protocol.error.ErrorCode;

public class RiskLimitException extends BetPlacementException {

  public RiskLimitException(String message) {
    super(ErrorCode.LIMIT_EXCEEDED, message == null ? "Risk limit exceeded" : message);
  }
}
