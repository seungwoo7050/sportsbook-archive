package com.sportsbook.betting.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BettingTopicsTest {

  @Test
  void matchesSharedTopology() {
    assertThat(BettingTopics.BET_PLACED).isEqualTo("bet.placed.v1");
    assertThat(BettingTopics.BET_SETTLED).isEqualTo("bet.settled.v1");
    assertThat(BettingTopics.BET_VOIDED).isEqualTo("bet.voided.v1");
    assertThat(BettingTopics.BET_RESOLUTION_REVISED).isEqualTo("bet.resolution.revised.v1");
    assertThat(BettingTopics.WALLET_DEBITED).isEqualTo("wallet.debited.v1");
    assertThat(BettingTopics.WALLET_DEBIT_FAILED).isEqualTo("wallet.debit-failed.v1");
  }
}
