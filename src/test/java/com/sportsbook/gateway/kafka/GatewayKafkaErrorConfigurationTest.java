package com.sportsbook.gateway.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

class GatewayKafkaErrorConfigurationTest {

  @Test
  void sendsPermanentContractFailuresDirectlyToRecovery() {
    GatewayKafkaProperties properties =
        new GatewayKafkaProperties(
            Duration.ofSeconds(1), 2, Duration.ofSeconds(11), Duration.ofSeconds(1));
    DefaultErrorHandler handler =
        new GatewayKafkaErrorConfiguration()
            .gatewayKafkaErrorHandler(mock(DeadLetterPublishingRecoverer.class), properties);

    assertThat(handler.removeClassification(GatewayEventContractException.class)).isFalse();
    assertThat(handler.isAckAfterHandle()).isTrue();
  }
}
