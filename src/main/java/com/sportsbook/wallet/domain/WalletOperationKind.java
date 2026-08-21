package com.sportsbook.wallet.domain;

/** Stable request identity for every money-moving wallet operation. */
public enum WalletOperationKind {
  DEPOSIT,
  WITHDRAW,
  BET_DEBIT,
  BET_PAYOUT,
  BET_REFUND,
  BET_FORFEIT,
  BET_ADJUSTMENT;

  public LedgerReason ledgerReason() {
    return LedgerReason.valueOf(name());
  }
}
