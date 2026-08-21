package com.sportsbook.risk.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Retains transient Redis and Kafka failures on the source partition for retry. */
@Configuration
public class KafkaConsumerConfiguration {
  static final long RETRY_INTERVAL_MILLIS = 1_000L;

  @Bean
  CommonErrorHandler riskKafkaErrorHandler() {
    DefaultErrorHandler handler = new DefaultErrorHandler(retryBackOff());
    handler.addRetryableExceptions(Exception.class);
    handler.setAckAfterHandle(false);
    handler.setCommitRecovered(false);
    return handler;
  }

  static FixedBackOff retryBackOff() {
    return new FixedBackOff(RETRY_INTERVAL_MILLIS, FixedBackOff.UNLIMITED_ATTEMPTS);
  }
}
