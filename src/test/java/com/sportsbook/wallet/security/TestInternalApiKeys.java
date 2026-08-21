package com.sportsbook.wallet.security;

import org.springframework.test.context.DynamicPropertyRegistry;

/** Supplies obvious test-only credentials to full application contexts. */
public final class TestInternalApiKeys {
  private TestInternalApiKeys() {}

  public static void register(DynamicPropertyRegistry registry) {
    registry.add("wallet.security.platform-api-key", () -> key("platform"));
    registry.add("wallet.security.gateway-api-key", () -> key("gateway"));
    registry.add("wallet.security.betting-service-api-key", () -> key("betting"));
    registry.add("wallet.security.settlement-service-api-key", () -> key("settlement"));
    registry.add("wallet.security.admin-api-key", () -> key("admin"));
  }

  private static String key(String caller) {
    return "test-only:" + caller + ":" + caller.repeat(8);
  }
}
