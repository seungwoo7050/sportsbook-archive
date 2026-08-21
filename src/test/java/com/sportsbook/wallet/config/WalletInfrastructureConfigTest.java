package com.sportsbook.wallet.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class WalletInfrastructureConfigTest {

  @Test
  void providesUtcClockAndRequestedTransactionManager() {
    WalletInfrastructureConfig config = new WalletInfrastructureConfig();
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

    assertThat(config.systemClock().getZone()).isEqualTo(ZoneOffset.UTC);
    assertThat(config.writeTransactionTemplate(manager).getTransactionManager()).isSameAs(manager);
  }
}
