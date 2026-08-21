package com.sportsbook.gateway.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Classifies permanent contract failures separately from transient delivery failures. */
@Configuration(proxyBeanMethods = false)
public class GatewayKafkaErrorConfiguration {

  @Bean
  DefaultErrorHandler gatewayKafkaErrorHandler(
      DeadLetterPublishingRecoverer recoverer, GatewayKafkaProperties properties) {
    DefaultErrorHandler handler =
        new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(properties.retryInterval().toMillis(), properties.retryAttempts()));
    handler.addNotRetryableExceptions(GatewayEventContractException.class);
    handler.setAckAfterHandle(true);
    handler.setResetStateOnRecoveryFailure(true);
    return handler;
  }
}
