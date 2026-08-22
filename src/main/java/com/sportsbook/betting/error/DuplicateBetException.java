package com.sportsbook.betting.error;

import com.sportsbook.protocol.error.ErrorCode;

public class DuplicateBetException extends BetPlacementException {

  public DuplicateBetException(String message) {
    super(ErrorCode.DUPLICATE_BET, message);
  }
}
