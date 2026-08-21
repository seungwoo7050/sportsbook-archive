package com.sportsbook.wallet.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit clock and transaction boundaries shared by wallet application services. */
@Configuration
public class WalletInfrastructureConfig {

  @Bean
  public Clock systemClock() {
    return Clock.systemUTC();
  }

  @Bean
  public TransactionTemplate writeTransactionTemplate(PlatformTransactionManager manager) {
    return new TransactionTemplate(manager);
  }
}
