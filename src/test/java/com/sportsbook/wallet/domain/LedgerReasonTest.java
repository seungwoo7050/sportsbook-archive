package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LedgerReasonTest {

  @Test
  void locksLedgerReasonVocabulary() {
    assertThat(LedgerReason.values())
        .containsExactly(
            LedgerReason.DEPOSIT,
            LedgerReason.WITHDRAW,
            LedgerReason.BET_DEBIT,
            LedgerReason.BET_PAYOUT,
            LedgerReason.BET_REFUND,
            LedgerReason.BET_FORFEIT,
            LedgerReason.BET_ADJUSTMENT);
  }
}
