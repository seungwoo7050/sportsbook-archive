package com.sportsbook.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class ProductionDefaultsTest {

  @Test
  void suppliesFailSafeRuntimeAndKafkaDefaults() throws IOException {
    PropertySourcesPropertyResolver properties = properties();

    assertThat(properties.getProperty("server.port")).isEqualTo("8084");
    assertThat(properties.getProperty("server.shutdown")).isEqualTo("graceful");
    assertThat(properties.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
        .isEqualTo("20s");
    assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(properties.getProperty("spring.kafka.consumer.enable-auto-commit"))
        .isEqualTo("false");
    assertThat(properties.getProperty("spring.kafka.consumer.auto-offset-reset"))
        .isEqualTo("earliest");
    assertThat(properties.getProperty("spring.kafka.consumer.isolation-level"))
        .isEqualTo("read_committed");
    assertThat(properties.getProperty("spring.kafka.consumer.properties.allow.auto.create.topics"))
        .isEqualTo("false");
    assertThat(properties.getProperty("spring.kafka.consumer.key-deserializer"))
        .endsWith("ByteArrayDeserializer");
    assertThat(properties.getProperty("spring.kafka.consumer.value-deserializer"))
        .endsWith("ByteArrayDeserializer");
    assertThat(properties.getProperty("spring.kafka.listener.ack-mode"))
        .isEqualTo("manual_immediate");
    assertThat(properties.getProperty("management.endpoint.health.probes.enabled"))
        .isEqualTo("true");
    assertThat(properties.getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health,info,prometheus");
    assertThat(properties.getProperty("settlement.wallet.base-url"))
        .isEqualTo("http://localhost:8081");
    assertThat(properties.getProperty("settlement.runtime.lease-duration")).isEqualTo("PT30S");
    assertThat(properties.getProperty("settlement.topics.bet-placed")).isEqualTo("bet.placed.v1");
  }

  @Test
  void leavesBothSecretsWithoutFallbackValues() throws IOException {
    String yaml =
        new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains("${SETTLEMENT_ADMIN_API_KEY}", "${SETTLEMENT_WALLET_API_KEY}")
        .doesNotContain("${SETTLEMENT_ADMIN_API_KEY:", "${SETTLEMENT_WALLET_API_KEY:");
  }

  private static PropertySourcesPropertyResolver properties() throws IOException {
    MutablePropertySources sources = new MutablePropertySources();
    new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yml"))
        .forEach(sources::addLast);
    return new PropertySourcesPropertyResolver(sources);
  }
}
