package com.sportsbook.wallet.domain;

/** Balance bucket addressed by a ledger entry. */
public enum BalanceBucket {
  /** Funds available for withdrawal or a new stake. */
  AVAILABLE,
  /** Funds held against an unsettled bet. */
  LOCKED
}
