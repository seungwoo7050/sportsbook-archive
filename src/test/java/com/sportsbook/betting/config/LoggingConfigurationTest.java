package com.sportsbook.betting.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LoggingConfigurationTest {

  @Test
  void offersReadableAndStructuredProfiles() throws IOException {
    String configuration =
        new ClassPathResource("logback-spring.xml").getContentAsString(StandardCharsets.UTF_8);

    assertThat(configuration)
        .contains("<springProfile name=\"!json\">", "<springProfile name=\"json\">")
        .contains("net.logstash.logback.encoder.LogstashEncoder")
        .contains("<includeMdcKeyName>traceId</includeMdcKeyName>")
        .contains("<includeMdcKeyName>spanId</includeMdcKeyName>")
        .contains("{\"service\":\"${appName}\"}");
  }
}
