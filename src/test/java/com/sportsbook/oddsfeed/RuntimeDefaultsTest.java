package com.sportsbook.oddsfeed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class RuntimeDefaultsTest {

  @Test
  void definesApprovedServiceEndpointDefaults() throws IOException {
    PropertySource<?> defaults =
        new YamlPropertySourceLoader()
            .load("runtime-defaults", new ClassPathResource("application.yml"))
            .get(0);

    assertThat(defaults.getProperty("server.port")).isEqualTo("${SERVER_PORT:8085}");
    assertThat(defaults.getProperty("server.shutdown")).isEqualTo("graceful");
    assertThat(defaults.getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health,prometheus");
    assertThat(defaults.getProperty("management.endpoint.health.show-details"))
        .isEqualTo("when-authorized");
    assertThat(defaults.getProperty("management.endpoint.health.probes.enabled")).isEqualTo(true);
    String readinessMembers =
        (String) defaults.getProperty("management.endpoint.health.group.readiness.include");
    assertThat(readinessMembers.split(",")).contains("readinessState", "redis");
    assertThat(defaults.getProperty("management.health.livenessstate.enabled")).isEqualTo(true);
    assertThat(defaults.getProperty("management.health.readinessstate.enabled")).isEqualTo(true);
    assertThat(defaults.getProperty("management.tracing.sampling.probability"))
        .isEqualTo("${OTEL_SAMPLING_PROBABILITY:1.0}");
    assertThat(defaults.getProperty("management.otlp.tracing.endpoint"))
        .isEqualTo("${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}");
  }
}
