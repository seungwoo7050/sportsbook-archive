package com.sportsbook.betting.error;

import com.sportsbook.protocol.error.ErrorCode;

public class InsufficientBalanceException extends BetPlacementException {

  public InsufficientBalanceException(String message) {
    super(ErrorCode.INSUFFICIENT_BALANCE, message == null ? "Insufficient balance" : message);
  }
}
