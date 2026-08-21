package com.sportsbook.wallet.security;

import com.sportsbook.wallet.domain.WalletCaller;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Environment-bound internal credentials, validated before any HTTP request is accepted. */
@ConfigurationProperties("wallet.security")
public final class WalletSecurityProperties {
  static final int MINIMUM_KEY_LENGTH = 32;

  private final Map<WalletCaller, String> apiKeys;

  public WalletSecurityProperties(
      String platformApiKey,
      String gatewayApiKey,
      String bettingServiceApiKey,
      String settlementServiceApiKey,
      String adminApiKey) {
    apiKeys =
        Map.of(
            WalletCaller.PLATFORM, requireKey(WalletCaller.PLATFORM, platformApiKey),
            WalletCaller.GATEWAY, requireKey(WalletCaller.GATEWAY, gatewayApiKey),
            WalletCaller.BETTING, requireKey(WalletCaller.BETTING, bettingServiceApiKey),
            WalletCaller.SETTLEMENT, requireKey(WalletCaller.SETTLEMENT, settlementServiceApiKey),
            WalletCaller.ADMIN, requireKey(WalletCaller.ADMIN, adminApiKey));
    if (new HashSet<>(apiKeys.values()).size() != apiKeys.size()) {
      throw new IllegalArgumentException("Wallet caller API keys must be distinct");
    }
  }

  String apiKey(WalletCaller caller) {
    return apiKeys.get(Objects.requireNonNull(caller, "caller"));
  }

  private static String requireKey(WalletCaller caller, String key) {
    if (key == null || key.isBlank() || key.length() < MINIMUM_KEY_LENGTH) {
      throw new IllegalArgumentException(
          caller.wireName()
              + " API key must contain at least "
              + MINIMUM_KEY_LENGTH
              + " characters");
    }
    return key;
  }
}
