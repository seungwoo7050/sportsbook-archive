package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WalletHttpPropertiesTest {

  @Test
  void defaultsToTimeoutsBelowTheSettlementLease() {
    WalletHttpProperties properties = new WalletHttpProperties(null, null);

    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void rejectsUnboundedOrDisabledTimeouts() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new WalletHttpProperties(Duration.ZERO, Duration.ofSeconds(1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new WalletHttpProperties(Duration.ofSeconds(1), Duration.ofSeconds(6)));
  }
}
