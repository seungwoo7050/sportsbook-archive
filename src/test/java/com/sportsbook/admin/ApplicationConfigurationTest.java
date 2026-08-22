package com.sportsbook.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class ApplicationConfigurationTest {

  @Test
  void bindsRuntimeEnvironmentIntoTheServiceConfiguration() throws IOException {
    MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test-runtime",
                Map.of(
                    "ADMIN_HTTP_PORT", "9190",
                    "ADMIN_DB_URL", "jdbc:postgresql://db/admin_test",
                    "ADMIN_KAFKA_BOOTSTRAP", "kafka:29092")));
    new YamlPropertySourceLoader()
        .load("admin", new ClassPathResource("application.yml"))
        .forEach(environment.getPropertySources()::addLast);

    assertThat(environment.getProperty("spring.application.name")).isEqualTo("admin-api");
    assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(9190);
    assertThat(environment.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:postgresql://db/admin_test");
    assertThat(environment.getProperty("spring.kafka.bootstrap-servers")).isEqualTo("kafka:29092");
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class))
        .isFalse();
  }
}
