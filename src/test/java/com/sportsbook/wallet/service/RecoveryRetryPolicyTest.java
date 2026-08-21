package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RecoveryRetryPolicyTest {
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void doublesFromTheFirstRetryUntilItsCap() {
    RecoveryRetryPolicy policy =
        new RecoveryRetryPolicy(Duration.ofMillis(100L), Duration.ofSeconds(2L));

    assertThat(policy.retryAt(NOW, 0)).isEqualTo(NOW.plusMillis(100L));
    assertThat(policy.retryAt(NOW, 3)).isEqualTo(NOW.plusMillis(800L));
    assertThat(policy.retryAt(NOW, 6)).isEqualTo(NOW.plusSeconds(2L));
    assertThat(policy.retryAt(NOW, Integer.MAX_VALUE)).isEqualTo(NOW.plusSeconds(2L));
    assertThatThrownBy(() -> policy.retryAt(NOW, -1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void defaultsToOneSecondWithAOneMinuteCap() {
    new ApplicationContextRunner()
        .withInitializer(
            context ->
                context
                    .getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
        .withBean(RecoveryRetryPolicy.class)
        .run(
            context -> {
              RecoveryRetryPolicy defaults = context.getBean(RecoveryRetryPolicy.class);
              assertThat(defaults.retryAt(NOW, 0)).isEqualTo(NOW.plusSeconds(1L));
              assertThat(defaults.retryAt(NOW, 7)).isEqualTo(NOW.plusSeconds(60L));
            });
  }
}
