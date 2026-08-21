package com.sportsbook.oddsfeed.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CachePropertiesTest {

  @Test
  void bindsProjectionTimeToLive() {
    var source = new MapConfigurationPropertySource(Map.of("oddsfeed.cache.ttl", "24h"));

    CacheProperties properties =
        new Binder(source)
            .bind("oddsfeed.cache", Bindable.of(CacheProperties.class))
            .orElseThrow(IllegalStateException::new);

    assertThat(properties.ttl()).isEqualTo(Duration.ofHours(24));
  }
}
