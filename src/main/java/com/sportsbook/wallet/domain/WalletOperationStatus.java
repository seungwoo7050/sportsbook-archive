package com.sportsbook.wallet.domain;

/** Durable outcome state. Only a blocked adjustment may transition after its first commit. */
public enum WalletOperationStatus {
  SUCCEEDED,
  REJECTED,
  BLOCKED_FUNDS
}
