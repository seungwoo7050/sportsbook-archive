package com.sportsbook.wallet.domain;

import java.util.Arrays;
import java.util.Optional;

/** Authenticated service identity participating in request fingerprints and audit records. */
public enum WalletCaller {
  PLATFORM("platform"),
  GATEWAY("gateway"),
  BETTING("betting-service"),
  SETTLEMENT("settlement-service"),
  ADMIN("admin-api");

  private final String wireName;

  WalletCaller(String wireName) {
    this.wireName = wireName;
  }

  public String wireName() {
    return wireName;
  }

  public static Optional<WalletCaller> fromWireName(String wireName) {
    return Arrays.stream(values()).filter(caller -> caller.wireName.equals(wireName)).findFirst();
  }
}
