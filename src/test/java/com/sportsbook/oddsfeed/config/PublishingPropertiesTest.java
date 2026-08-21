package com.sportsbook.oddsfeed.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class PublishingPropertiesTest {

  @Test
  void bindsTopicsAndPublishingLimits() {
    var source =
        new MapConfigurationPropertySource(
            Map.of(
                "oddsfeed.kafka.topics.odds-changed", "odds.changed.v1",
                "oddsfeed.kafka.topics.market-status-changed", "market.status.changed.v1",
                "oddsfeed.kafka.topics.event-lifecycle", "event.lifecycle.v1",
                "oddsfeed.kafka.topics.match-result", "match.result.v1",
                "oddsfeed.publish.odds-change-threshold", "0.01",
                "oddsfeed.publish.broker-ack-timeout", "3s"));
    Binder binder = new Binder(source);

    KafkaTopicsProperties topics =
        binder
            .bind("oddsfeed.kafka.topics", Bindable.of(KafkaTopicsProperties.class))
            .orElseThrow(IllegalStateException::new);
    PublishProperties publishing =
        binder
            .bind("oddsfeed.publish", Bindable.of(PublishProperties.class))
            .orElseThrow(IllegalStateException::new);

    assertThat(topics.oddsChanged()).isEqualTo("odds.changed.v1");
    assertThat(topics.marketStatusChanged()).isEqualTo("market.status.changed.v1");
    assertThat(topics.eventLifecycle()).isEqualTo("event.lifecycle.v1");
    assertThat(topics.matchResult()).isEqualTo("match.result.v1");
    assertThat(publishing.oddsChangeThreshold()).isEqualByComparingTo(new BigDecimal("0.01"));
    assertThat(publishing.brokerAckTimeout()).isEqualTo(Duration.ofSeconds(3));
  }
}
