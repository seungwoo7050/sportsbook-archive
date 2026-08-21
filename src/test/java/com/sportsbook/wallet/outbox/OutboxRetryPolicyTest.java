package com.sportsbook.wallet.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OutboxRetryPolicyTest {

  private final OutboxRetryPolicy policy =
      new OutboxRetryPolicy(Duration.ofMillis(100), Duration.ofSeconds(2));

  @Test
  void doublesUntilTheConfiguredCapForAnyAttempt() {
    assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofMillis(100));
    assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofMillis(800));
    assertThat(policy.delayForAttempt(6)).isEqualTo(Duration.ofSeconds(2));
    assertThat(policy.delayForAttempt(Integer.MAX_VALUE)).isEqualTo(Duration.ofSeconds(2));
  }

  @Test
  void defaultsToOneSecondAndCapsAtOneMinute() {
    new ApplicationContextRunner()
        .withInitializer(
            context ->
                context
                    .getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
        .withBean(OutboxRetryPolicy.class)
        .run(
            context -> {
              OutboxRetryPolicy defaults = context.getBean(OutboxRetryPolicy.class);
              assertThat(defaults.delayForAttempt(1)).isEqualTo(Duration.ofSeconds(1L));
              assertThat(defaults.delayForAttempt(7)).isEqualTo(Duration.ofSeconds(60L));
              assertThat(defaults.delayForAttempt(Integer.MAX_VALUE))
                  .isEqualTo(Duration.ofSeconds(60L));
            });
    assertThatThrownBy(() -> new OutboxRetryPolicy(Duration.ofNanos(1L), Duration.ofSeconds(1L)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void storesOnlyABoundedSingleLineFailureDescription() {
    String description = policy.describe(new IllegalStateException("failed\n" + "x".repeat(2000)));

    assertThat(description).startsWith("IllegalStateException: failed x").hasSize(1024);
  }
}
