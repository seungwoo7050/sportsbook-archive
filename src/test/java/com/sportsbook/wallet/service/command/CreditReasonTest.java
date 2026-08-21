package com.sportsbook.wallet.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreditReasonTest {

  @Test
  void locksTheCreditReasonVocabulary() {
    assertThat(CreditReason.values())
        .containsExactly(CreditReason.PAYOUT, CreditReason.VOID, CreditReason.REFUND);
  }
}
