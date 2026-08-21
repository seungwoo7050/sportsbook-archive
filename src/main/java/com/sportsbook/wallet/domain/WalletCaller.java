package com.sportsbook.wallet.domain;

/** Authenticated service identity participating in request fingerprints and audit records. */
public enum WalletCaller {
  PLATFORM,
  GATEWAY,
  BETTING,
  SETTLEMENT,
  ADMIN
}
