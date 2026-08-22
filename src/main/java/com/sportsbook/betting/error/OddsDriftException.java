package com.sportsbook.betting.error;

import com.sportsbook.protocol.error.ErrorCode;

public class OddsDriftException extends BetPlacementException {

  public OddsDriftException(String message) {
    super(ErrorCode.ODDS_DRIFT, message);
  }
}
