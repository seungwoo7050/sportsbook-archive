package com.sportsbook.oddsfeed.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class RealPropertiesTest {

  @Test
  void bindsExternalProviderSettings() {
    Map<String, String> values =
        Map.of(
            "oddsfeed.real.api-key", "secret",
            "oddsfeed.real.base-url", "https://odds.example",
            "oddsfeed.real.sport-keys[0]", "soccer_epl",
            "oddsfeed.real.sport-keys[1]", "basketball_nba",
            "oddsfeed.real.rate-limit.max-requests-per-minute", "5",
            "oddsfeed.real.monthly-quota", "500",
            "oddsfeed.real.poll-interval-seconds", "60");

    RealProperties properties =
        new Binder(new MapConfigurationPropertySource(values))
            .bind("oddsfeed.real", Bindable.of(RealProperties.class))
            .orElseThrow(IllegalStateException::new);

    assertThat(properties.apiKey()).isEqualTo("secret");
    assertThat(properties.baseUrl()).isEqualTo("https://odds.example");
    assertThat(properties.sportKeys()).containsExactly("soccer_epl", "basketball_nba");
    assertThat(properties.rateLimit().maxRequestsPerMinute()).isEqualTo(5);
    assertThat(properties.monthlyQuota()).isEqualTo(500);
    assertThat(properties.pollIntervalSeconds()).isEqualTo(60);
  }
}
