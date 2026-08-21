package com.sportsbook.wallet.domain.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletProofNotFoundExceptionTest {

  @Test
  void identifiesTheMissingDebitBet() {
    UUID betId = UUID.fromString("019b76da-a000-7000-8000-000000000301");
    RuntimeException missing = new WalletOperationNotFoundException(betId);

    assertThat(missing).isExactlyInstanceOf(WalletOperationNotFoundException.class);
    assertThat(((WalletOperationNotFoundException) missing).betId()).isSameAs(betId);
    assertThat(missing).hasMessage("No wallet debit exists for bet " + betId);
    assertThatNullPointerException().isThrownBy(() -> new WalletOperationNotFoundException(null));
  }

  @Test
  void identifiesTheMissingAdjustmentRevision() {
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000302");
    RuntimeException missing = new WalletAdjustmentNotFoundException(revisionId);

    assertThat(missing).isExactlyInstanceOf(WalletAdjustmentNotFoundException.class);
    assertThat(((WalletAdjustmentNotFoundException) missing).revisionId()).isSameAs(revisionId);
    assertThat(missing).hasMessage("No wallet adjustment exists for revision " + revisionId);
    assertThatNullPointerException().isThrownBy(() -> new WalletAdjustmentNotFoundException(null));
  }
}
