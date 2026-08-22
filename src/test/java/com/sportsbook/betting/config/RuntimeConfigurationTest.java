package com.sportsbook.betting.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class RuntimeConfigurationTest {

  @Test
  void wiresDurableStoresAndRawKafkaConsumers() throws IOException {
    List<PropertySource<?>> sources = load("application.yml");

    assertThat(value(sources, "spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(value(sources, "spring.flyway.locations")).isEqualTo("classpath:db/migration");
    assertThat(value(sources, "spring.kafka.consumer.enable-auto-commit")).isEqualTo(false);
    assertThat(value(sources, "spring.kafka.consumer.value-deserializer"))
        .isEqualTo("org.apache.kafka.common.serialization.ByteArrayDeserializer");
    assertThat(value(sources, "spring.kafka.listener.ack-mode")).isEqualTo("record");
  }

  @Test
  void requiresDistinctCallerSecretsWithoutProductionDefaults() throws IOException {
    List<PropertySource<?>> sources = load("application.yml");

    assertThat(value(sources, "betting.clients.risk-api-key")).isEqualTo("${BETTING_RISK_API_KEY}");
    assertThat(value(sources, "betting.clients.wallet-api-key"))
        .isEqualTo("${BETTING_WALLET_API_KEY}");
  }

  private static List<PropertySource<?>> load(String resource) throws IOException {
    return new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
  }

  private static Object value(List<PropertySource<?>> sources, String name) {
    return sources.stream()
        .map(source -> source.getProperty(name))
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }
}
