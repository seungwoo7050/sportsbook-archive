package com.sportsbook.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sportsbook.settlement.event.ExactDeadLetterRecoverer;
import com.sportsbook.settlement.event.KafkaRetryPolicy;
import com.sportsbook.settlement.event.MessageFailureClassifier;
import java.sql.SQLTransientException;
import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

class KafkaRecoveryConfigurationTest {

  private final KafkaRecoveryConfiguration configuration = new KafkaRecoveryConfiguration();

  @Test
  void createsAnAcknowledgingHandlerOnlyAfterSuccessfulRecovery() {
    @SuppressWarnings("unchecked")
    KafkaOperations<byte[], byte[]> operations = mock(KafkaOperations.class);
    KafkaRetryPolicy policy =
        new KafkaRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(11));
    MessageFailureClassifier classifier = new MessageFailureClassifier();
    ExactDeadLetterRecoverer recoverer = configuration.exactDeadLetterRecoverer(operations, policy);

    DefaultErrorHandler handler =
        configuration.settlementKafkaErrorHandler(recoverer, policy, classifier);

    assertThat(handler.isAckAfterHandle()).isTrue();
  }

  @Test
  void refusesNonRawListenerRecords() {
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>("bet.placed.v1", 0, 0, "key", new byte[0]);

    assertThatThrownBy(() -> KafkaRecoveryConfiguration.rawRecord(record))
        .isInstanceOf(KafkaException.class)
        .hasMessageContaining("raw byte");
  }

  @Test
  void leavesTransientFailuresUncommittedInsteadOfDeadLettering() {
    @SuppressWarnings("unchecked")
    KafkaOperations<byte[], byte[]> operations = mock(KafkaOperations.class);
    ExactDeadLetterRecoverer recoverer =
        new ExactDeadLetterRecoverer(operations, "settlement-service", Duration.ofSeconds(1));
    ConsumerRecord<byte[], byte[]> record =
        new ConsumerRecord<>("bet.placed.v1", 0, 0, new byte[0], new byte[0]);

    assertThatThrownBy(
            () ->
                KafkaRecoveryConfiguration.recover(
                    record,
                    new SQLTransientException("database unavailable"),
                    recoverer,
                    new MessageFailureClassifier()))
        .isInstanceOf(KafkaException.class)
        .hasMessageContaining("remains uncommitted");
    verifyNoInteractions(operations);
  }

  @Test
  void retainsRawKafkaTombstonesForPermanentRecovery() {
    ConsumerRecord<byte[], byte[]> tombstone =
        new ConsumerRecord<>("match.result.v1", 2, 9, new byte[0], null);

    assertThat(KafkaRecoveryConfiguration.rawRecord(tombstone).value()).isNull();
  }
}
