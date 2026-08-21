package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WalletOperationVocabularyTest {

  @Test
  void locksOperationKindsAndOutcomeStates() {
    assertThat(WalletOperationKind.values())
        .containsExactly(
            WalletOperationKind.DEPOSIT,
            WalletOperationKind.WITHDRAW,
            WalletOperationKind.BET_DEBIT,
            WalletOperationKind.BET_PAYOUT,
            WalletOperationKind.BET_REFUND,
            WalletOperationKind.BET_FORFEIT,
            WalletOperationKind.BET_ADJUSTMENT)
        .allSatisfy(kind -> assertThat(kind.ledgerReason().name()).isEqualTo(kind.name()));
    assertThat(WalletOperationStatus.values())
        .containsExactly(
            WalletOperationStatus.SUCCEEDED,
            WalletOperationStatus.REJECTED,
            WalletOperationStatus.BLOCKED_FUNDS);
  }
}
