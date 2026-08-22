package com.sportsbook.betting.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.error.ErrorCode;
import org.junit.jupiter.api.Test;

class OddsVerdictTest {

  @Test
  void mapsPriceAndAvailabilityFailures() {
    assertThat(new OddsDriftException("moved").errorCode()).isEqualTo(ErrorCode.ODDS_DRIFT);
    assertThat(new MarketClosedException("closed").errorCode()).isEqualTo(ErrorCode.EVENT_CLOSED);
  }
}
