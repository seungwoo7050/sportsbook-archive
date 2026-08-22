package com.sportsbook.settlement.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.sql.SQLTransientException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

class KafkaRetryPolicyTest {

  private final MessageFailureClassifier classifier = new MessageFailureClassifier();

  @Test
  void givesTransientFailuresThreeTotalDeliveryAttempts() {
    KafkaRetryPolicy policy =
        new KafkaRetryPolicy(3, Duration.ofMillis(250), Duration.ofSeconds(11));
    BackOffExecution execution =
        policy.backOffFor(new SQLTransientException("retry"), classifier).start();

    assertThat(execution.nextBackOff()).isEqualTo(250);
    assertThat(execution.nextBackOff()).isEqualTo(250);
    assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
  }

  @Test
  void sendsPermanentContractsDirectlyToRecovery() {
    BackOffExecution execution =
        new KafkaRetryPolicy(3, null, null)
            .backOffFor(new IllegalArgumentException("poison"), classifier)
            .start();

    assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
  }

  @Test
  void rejectsUnsafeRetryBounds() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new KafkaRetryPolicy(11, Duration.ZERO, Duration.ZERO));
  }
}
