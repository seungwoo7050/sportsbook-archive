package com.sportsbook.oddsfeed.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CriticalDeliveryPropertiesTest {

  @Test
  void bindsDurableStreamSettings() {
    var source =
        new MapConfigurationPropertySource(
            Map.of(
                "oddsfeed.delivery.stream-key", "critical-events",
                "oddsfeed.delivery.consumer-group", "publisher",
                "oddsfeed.delivery.consumer-name", "publisher-1",
                "oddsfeed.delivery.batch-size", "25",
                "oddsfeed.delivery.claim-idle", "10s"));

    CriticalDeliveryProperties properties =
        new Binder(source)
            .bind("oddsfeed.delivery", Bindable.of(CriticalDeliveryProperties.class))
            .orElseThrow(IllegalStateException::new);

    assertThat(properties.streamKey()).isEqualTo("critical-events");
    assertThat(properties.consumerGroup()).isEqualTo("publisher");
    assertThat(properties.consumerName()).isEqualTo("publisher-1");
    assertThat(properties.batchSize()).isEqualTo(25);
    assertThat(properties.claimIdle()).isEqualTo(Duration.ofSeconds(10));
  }
}
