package com.sportsbook.settlement.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("settlement.wallet.http")
public record WalletHttpProperties(Duration connectTimeout, Duration readTimeout) {

  private static final Duration MAX_TIMEOUT = Duration.ofSeconds(5);

  public WalletHttpProperties {
    connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
    readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    if (invalid(connectTimeout) || invalid(readTimeout)) {
      throw new IllegalArgumentException("Wallet HTTP timeouts must be in (0, 5s]");
    }
  }

  private static boolean invalid(Duration timeout) {
    return timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0;
  }
}
