package com.sportsbook.wallet.security;

import com.sportsbook.wallet.domain.WalletCaller;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Supplies obvious test-only credentials to full application contexts. */
public final class TestInternalApiKeys {
  private TestInternalApiKeys() {}

  public static void register(DynamicPropertyRegistry registry) {
    registry.add("wallet.security.platform-api-key", () -> key(WalletCaller.PLATFORM));
    registry.add("wallet.security.gateway-api-key", () -> key(WalletCaller.GATEWAY));
    registry.add("wallet.security.betting-service-api-key", () -> key(WalletCaller.BETTING));
    registry.add("wallet.security.settlement-service-api-key", () -> key(WalletCaller.SETTLEMENT));
    registry.add("wallet.security.admin-api-key", () -> key(WalletCaller.ADMIN));
  }

  static String key(WalletCaller caller) {
    return "test-only:" + caller.wireName() + ":" + caller.name().repeat(8);
  }
}
