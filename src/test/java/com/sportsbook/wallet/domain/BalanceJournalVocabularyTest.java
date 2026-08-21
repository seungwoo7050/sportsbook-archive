package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BalanceJournalVocabularyTest {

  @Test
  void locksBalanceBucketsAndJournalSides() {
    assertThat(BalanceBucket.values())
        .containsExactly(BalanceBucket.AVAILABLE, BalanceBucket.LOCKED);
    assertThat(LedgerSide.values()).containsExactly(LedgerSide.DEBIT, LedgerSide.CREDIT);
  }
}
