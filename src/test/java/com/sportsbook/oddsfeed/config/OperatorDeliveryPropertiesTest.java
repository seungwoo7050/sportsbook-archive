package com.sportsbook.oddsfeed.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class OperatorDeliveryPropertiesTest {

  @Test
  void bindsOperatorStreamSettings() {
    var source =
        new MapConfigurationPropertySource(
            Map.of(
                "oddsfeed.operator.delivery.stream-key", "operator-actions",
                "oddsfeed.operator.delivery.consumer-group", "publisher",
                "oddsfeed.operator.delivery.consumer-name", "publisher-1",
                "oddsfeed.operator.delivery.batch-size", "25",
                "oddsfeed.operator.delivery.claim-idle", "10s",
                "oddsfeed.operator.delivery.poll-interval-ms", "500"));

    OperatorDeliveryProperties properties =
        new Binder(source)
            .bind("oddsfeed.operator.delivery", Bindable.of(OperatorDeliveryProperties.class))
            .orElseThrow(IllegalStateException::new);

    assertThat(properties.streamKey()).isEqualTo("operator-actions");
    assertThat(properties.consumerGroup()).isEqualTo("publisher");
    assertThat(properties.consumerName()).isEqualTo("publisher-1");
    assertThat(properties.batchSize()).isEqualTo(25);
    assertThat(properties.claimIdle()).isEqualTo(Duration.ofSeconds(10));
    assertThat(properties.pollIntervalMs()).isEqualTo(500);
  }
}
