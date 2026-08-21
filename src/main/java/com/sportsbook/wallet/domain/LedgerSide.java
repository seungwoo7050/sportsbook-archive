package com.sportsbook.wallet.domain;

/** Asset-side direction of a double-entry journal row. */
public enum LedgerSide {
  /** The addressed bucket increases. */
  DEBIT,
  /** The addressed bucket decreases. */
  CREDIT
}
