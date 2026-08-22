package com.sportsbook.betting.error;

import com.sportsbook.protocol.error.ErrorCode;

public class MarketClosedException extends BetPlacementException {

  public MarketClosedException(String message) {
    super(ErrorCode.EVENT_CLOSED, message);
  }
}
