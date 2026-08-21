package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

class KafkaConsumerConfigurationTest {
  @Test
  void transientFailuresRemainUnacknowledgedForRetry() {
    CommonErrorHandler handler = new KafkaConsumerConfiguration().riskKafkaErrorHandler();

    assertThat(handler).isInstanceOf(DefaultErrorHandler.class);
    assertThat(handler.isAckAfterHandle()).isFalse();
    FixedBackOff backOff = KafkaConsumerConfiguration.retryBackOff();
    assertThat(backOff.getInterval()).isEqualTo(1_000L);
    assertThat(backOff.getMaxAttempts()).isEqualTo(FixedBackOff.UNLIMITED_ATTEMPTS);
  }
}
