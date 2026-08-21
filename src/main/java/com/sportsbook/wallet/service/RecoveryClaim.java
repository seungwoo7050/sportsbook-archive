package com.sportsbook.wallet.service;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.AdjustmentStatus;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.domain.WalletOperationStatus;
import java.math.BigInteger;

/** One account-locked FIFO head and its mutable operation outcome. */
record RecoveryClaim(
    Account account, WalletAdjustment proof, WalletOperation operation, Money amount) {

  static RecoveryClaim locked(Account account, WalletAdjustment proof, WalletOperation operation) {
    if (!account.userId().equals(proof.userId())
        || !proof.userId().equals(operation.userId())
        || !proof.idempotencyKey().equals(operation.idempotencyKey())) {
      throw new IllegalStateException("Recovery claim identity is inconsistent");
    }
    if (proof.status() != AdjustmentStatus.BLOCKED
        || proof.deltaAmount() >= 0L
        || operation.status() != WalletOperationStatus.BLOCKED_FUNDS
        || operation.kind() != WalletOperationKind.BET_ADJUSTMENT
        || operation.caller() != WalletCaller.SETTLEMENT) {
      throw new IllegalStateException("Recovery claim state is inconsistent");
    }
    Money amount = new Money(-proof.deltaAmount(), proof.currency());
    if (!operation.requestAmount().equals(amount)
        || account.currency() != amount.currency()
        || account.recoveryDebtAmount().compareTo(BigInteger.valueOf(amount.amount())) < 0) {
      throw new IllegalStateException("Recovery claim amount is inconsistent");
    }
    return new RecoveryClaim(account, proof, operation, amount);
  }
}
