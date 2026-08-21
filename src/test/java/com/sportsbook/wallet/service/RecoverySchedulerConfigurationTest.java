package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class RecoverySchedulerConfigurationTest {
  @Test
  void productionConfigurationDefaultsToAutomaticRecovery() throws IOException {
    var properties =
        new YamlPropertySourceLoader()
            .load("wallet", new ClassPathResource("application.yml"))
            .get(0);

    assertThat(properties.getProperty("wallet.recovery.scheduling-enabled"))
        .isEqualTo("${WALLET_RECOVERY_ENABLED:true}");
    assertThat(properties.getProperty("wallet.recovery.poll-interval"))
        .isEqualTo("${WALLET_RECOVERY_POLL_INTERVAL:PT1S}");
    assertThat(properties.getProperty("wallet.recovery.retry-cap"))
        .isEqualTo("${WALLET_RECOVERY_RETRY_CAP:PT60S}");
  }

  @Test
  void schedulingPropertyControlsTheWorkerPoller() {
    ApplicationContextRunner context =
        new ApplicationContextRunner()
            .withBean(RecoveryWorker.class, () -> mock(RecoveryWorker.class))
            .withUserConfiguration(RecoveryScheduler.class);

    context
        .withPropertyValues("wallet.recovery.scheduling-enabled=true")
        .run(enabled -> assertThat(enabled.getBeansOfType(RecoveryScheduler.class)).hasSize(1));
    context
        .withPropertyValues("wallet.recovery.scheduling-enabled=false")
        .run(disabled -> assertThat(disabled.getBeansOfType(RecoveryScheduler.class)).isEmpty());
  }
}
