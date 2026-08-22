package com.sportsbook.betting.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.error.ErrorCode;
import org.junit.jupiter.api.Test;

class BusinessVerdictTest {

  @Test
  void mapsWalletDeclineAndReplayConflict() {
    assertThat(new InsufficientBalanceException(null).errorCode())
        .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
    assertThat(new DuplicateBetException("reused").errorCode()).isEqualTo(ErrorCode.DUPLICATE_BET);
  }
}
