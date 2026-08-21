package com.sportsbook.oddsfeed.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class MockPropertiesTest {

  @Test
  void bindsDeterministicSimulationSettings() {
    Map<String, String> values =
        Map.of(
            "oddsfeed.mock.minutes-per-second", "2",
            "oddsfeed.mock.scenarios.auto-rotate", "true",
            "oddsfeed.mock.scenarios.rotation-interval-seconds", "30",
            "oddsfeed.mock.base-home-win-probability", "0.45",
            "oddsfeed.mock.base-draw-probability", "0.25",
            "oddsfeed.mock.base-away-win-probability", "0.30",
            "oddsfeed.mock.random-seed", "424242",
            "oddsfeed.mock.tick-interval-ms", "500");

    MockProperties properties =
        new Binder(new MapConfigurationPropertySource(values))
            .bind("oddsfeed.mock", Bindable.of(MockProperties.class))
            .orElseThrow(IllegalStateException::new);

    assertThat(properties.minutesPerSecond()).isEqualTo(2);
    assertThat(properties.randomSeed()).isEqualTo(424242);
    assertThat(properties.scenarios().autoRotate()).isTrue();
    assertThat(properties.scenarios().rotationIntervalSeconds()).isEqualTo(30);
  }
}
