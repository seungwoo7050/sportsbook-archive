package com.sportsbook.wallet.domain;

import java.util.Set;
import java.util.UUID;

/** Stable ledger counterparties that are not user accounts. */
public final class SystemAccountIds {

  public static final UUID HOUSE = UUID.fromString("00000000-0000-7000-8000-000000000001");
  public static final UUID EXTERNAL_PAYMENT =
      UUID.fromString("00000000-0000-7000-8000-000000000002");

  private static final Set<UUID> ALL = Set.of(HOUSE, EXTERNAL_PAYMENT);

  public static boolean isSystemAccount(UUID accountId) {
    return ALL.contains(accountId);
  }

  private SystemAccountIds() {}
}
