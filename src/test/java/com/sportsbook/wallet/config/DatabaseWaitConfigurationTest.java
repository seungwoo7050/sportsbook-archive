package com.sportsbook.wallet.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DatabaseWaitConfigurationTest {

  @Test
  void boundsConnectionLockAndStatementWaits() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .run(
            context -> {
              HikariConfig config =
                  Binder.get(context.getEnvironment())
                      .bind("spring.datasource.hikari", Bindable.of(HikariConfig.class))
                      .orElseThrow(() -> new IllegalStateException("Hikari settings not bound"));

              assertThat(config.getConnectionTimeout()).isEqualTo(2_000L);
              assertThat(config.getConnectionInitSql())
                  .contains("lock_timeout TO '2s'", "statement_timeout TO '5s'");
            });
  }
}
