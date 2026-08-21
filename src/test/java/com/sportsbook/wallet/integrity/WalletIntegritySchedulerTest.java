package com.sportsbook.wallet.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class WalletIntegritySchedulerTest {

  @Test
  void productionConfigurationEnablesPeriodicScans() throws IOException {
    var properties =
        new YamlPropertySourceLoader()
            .load("wallet", new ClassPathResource("application.yml"))
            .get(0);

    assertThat(properties.getProperty("wallet.integrity.scheduling-enabled"))
        .isEqualTo("${WALLET_INTEGRITY_ENABLED:true}");
    assertThat(properties.getProperty("wallet.integrity.poll-interval"))
        .isEqualTo("${WALLET_INTEGRITY_POLL_INTERVAL:PT30S}");
  }

  @Test
  void schedulingPropertyControlsTheScanner() {
    ApplicationContextRunner context =
        new ApplicationContextRunner()
            .withBean(WalletIntegrityScanner.class, () -> mock(WalletIntegrityScanner.class))
            .withBean(WalletIntegrityMetrics.class, () -> mock(WalletIntegrityMetrics.class))
            .withUserConfiguration(WalletIntegrityScheduler.class);

    context
        .withPropertyValues("wallet.integrity.scheduling-enabled=true")
        .run(
            enabled ->
                assertThat(enabled.getBeansOfType(WalletIntegrityScheduler.class)).hasSize(1));
    context
        .withPropertyValues("wallet.integrity.scheduling-enabled=false")
        .run(
            disabled ->
                assertThat(disabled.getBeansOfType(WalletIntegrityScheduler.class)).isEmpty());
  }

  @Test
  void recordsCompletedAndFailedScans() {
    WalletIntegrityScanner scanner = mock(WalletIntegrityScanner.class);
    WalletIntegrityMetrics metrics = new WalletIntegrityMetrics(new SimpleMeterRegistry());
    WalletIntegrityScheduler scheduler = new WalletIntegrityScheduler(scanner, metrics);
    WalletIntegritySnapshot snapshot =
        new WalletIntegritySnapshot(Instant.parse("2026-08-21T14:00:00Z"), 0, 0, 0, 0, 0, 0, 0, 0);
    IllegalStateException failure = new IllegalStateException("database unavailable");
    when(scanner.scan()).thenReturn(snapshot).thenThrow(failure);

    scheduler.scan();
    assertThat(metrics.status()).isEqualTo(new WalletIntegrityMetrics.Status(snapshot, false));

    assertThatThrownBy(scheduler::scan).isSameAs(failure);
    assertThat(metrics.status()).isEqualTo(new WalletIntegrityMetrics.Status(snapshot, true));
  }
}
