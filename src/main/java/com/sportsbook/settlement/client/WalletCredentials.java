package com.sportsbook.settlement.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("settlement.wallet")
public record WalletCredentials(String apiKey) {

  public static final String CALLER = "settlement-service";
  private static final int MINIMUM_SECRET_LENGTH = 32;

  public WalletCredentials {
    if (apiKey == null || apiKey.isBlank() || apiKey.length() < MINIMUM_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "SETTLEMENT_WALLET_API_KEY must contain at least 32 characters");
    }
  }

  @Override
  public String toString() {
    return "WalletCredentials[apiKey=<redacted>]";
  }
}
