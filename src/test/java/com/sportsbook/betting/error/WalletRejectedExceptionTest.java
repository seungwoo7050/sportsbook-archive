package com.sportsbook.betting.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.error.ErrorCode;
import org.junit.jupiter.api.Test;

class WalletRejectedExceptionTest {

  @Test
  void keepsProviderCodeForDurableVerdict() {
    WalletRejectedException exception =
        new WalletRejectedException("WALLET_ACCOUNT_NOT_FOUND", "account missing");

    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThat(exception.walletErrorCode()).isEqualTo("WALLET_ACCOUNT_NOT_FOUND");
    assertThat(exception).hasMessage("account missing");
  }

  @Test
  void identifiesRetryableProofMismatchForOperations() {
    WalletProofMismatchException mismatch = new WalletProofMismatchException("refund");

    assertThat(mismatch.errorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    assertThat(mismatch.operation()).isEqualTo("refund");
  }
}
