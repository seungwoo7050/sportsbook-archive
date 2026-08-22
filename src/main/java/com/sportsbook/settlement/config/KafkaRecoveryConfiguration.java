package com.sportsbook.settlement.config;

import com.sportsbook.settlement.event.ExactDeadLetterRecoverer;
import com.sportsbook.settlement.event.KafkaRetryPolicy;
import com.sportsbook.settlement.event.MessageFailureClassifier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

/** Connects failure classification, bounded retries, and exact dead-letter recovery. */
@Configuration
public class KafkaRecoveryConfiguration {

  static final String CONSUMER_GROUP = "settlement-service";

  @Bean
  MessageFailureClassifier messageFailureClassifier() {
    return new MessageFailureClassifier();
  }

  @Bean
  ExactDeadLetterRecoverer exactDeadLetterRecoverer(
      @Qualifier(RawKafkaProducerConfiguration.OPERATIONS)
          KafkaOperations<byte[], byte[]> operations,
      KafkaRetryPolicy policy) {
    return new ExactDeadLetterRecoverer(operations, CONSUMER_GROUP, policy.dltSendTimeout());
  }

  @Bean
  DefaultErrorHandler settlementKafkaErrorHandler(
      ExactDeadLetterRecoverer recoverer,
      KafkaRetryPolicy policy,
      MessageFailureClassifier classifier) {
    DefaultErrorHandler handler =
        new DefaultErrorHandler(
            (record, failure) -> recover(record, failure, recoverer, classifier),
            policy.backOffFor(new IllegalStateException("initial"), classifier));
    handler.setBackOffFunction((record, failure) -> policy.backOffFor(failure, classifier));
    handler.setCommitRecovered(true);
    handler.setAckAfterHandle(true);
    handler.setResetStateOnRecoveryFailure(true);
    return handler;
  }

  static void recover(
      ConsumerRecord<?, ?> record,
      Exception failure,
      ExactDeadLetterRecoverer recoverer,
      MessageFailureClassifier classifier) {
    if (classifier.classify(failure) != MessageFailureClassifier.Disposition.PERMANENT) {
      throw new KafkaException("Transient listener failure remains uncommitted", failure);
    }
    recoverer.recover(rawRecord(record), failure);
  }

  @SuppressWarnings("unchecked")
  static ConsumerRecord<byte[], byte[]> rawRecord(ConsumerRecord<?, ?> record) {
    if ((record.key() != null && !(record.key() instanceof byte[]))
        || (record.value() != null && !(record.value() instanceof byte[]))) {
      throw new KafkaException("Settlement listeners require raw byte keys and values");
    }
    return (ConsumerRecord<byte[], byte[]>) record;
  }
}
