package com.sportsbook.wallet.domain;

/** Business reason recorded on both rows of a ledger pair. */
public enum LedgerReason {
  DEPOSIT,
  WITHDRAW,
  BET_DEBIT,
  BET_PAYOUT,
  BET_REFUND,
  BET_FORFEIT,
  BET_ADJUSTMENT
}
