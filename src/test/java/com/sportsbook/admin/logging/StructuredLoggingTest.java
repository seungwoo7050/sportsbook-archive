package com.sportsbook.admin.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    classes = StructuredLoggingTest.LoggingApplication.class,
    properties = "management.endpoint.health.group.readiness.include=readinessState",
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StructuredLoggingTest {

  private static final Logger log = LoggerFactory.getLogger(StructuredLoggingTest.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void redactsHeadersCredentialsAndStackTraces(CapturedOutput output) throws Exception {
    List<String> secrets =
        List.of(
            "authorization.fixture.secret",
            "idempotency.fixture.secret",
            "api.header.fixture.secret",
            "internal.api.fixture.secret",
            "api.label.fixture.secret",
            "password.fixture.secret",
            "token.fixture.secret",
            "bare.bearer.fixture.secret",
            "mdc.fixture.secret",
            "stack.fixture.secret");
    MDC.put("traceId", "fixture-trace-id");
    MDC.put("spanId", "fixture-span-id");
    MDC.put("adminActionId", "018f0000-0000-7000-8000-000000000093");
    MDC.put("authorization", secrets.get(8));
    try {
      log.info(
          "audit-marker Authorization: Bearer {}, Idempotency-Key={}, X-API-Key={}, "
              + "X-Internal-Api-Key={}, apiKey={}, password={}, token={}, standalone Bearer {}",
          secrets.get(0),
          secrets.get(1),
          secrets.get(2),
          secrets.get(3),
          secrets.get(4),
          secrets.get(5),
          secrets.get(6),
          secrets.get(7),
          new IllegalStateException("X-API-Key: " + secrets.get(9)));
    } finally {
      MDC.clear();
    }

    JsonNode event = event(output, "audit-marker");
    assertThat(event.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "@timestamp",
            "level",
            "logger_name",
            "message",
            "stack_trace",
            "traceId",
            "spanId",
            "adminActionId",
            "service");
    assertThat(event.path("service").asText()).isEqualTo("admin-api");
    assertThat(event.path("message").asText()).contains("[REDACTED]");
    assertThat(event.path("stack_trace").asText()).contains("IllegalStateException", "[REDACTED]");
    assertThat(event.has("authorization")).isFalse();
    assertThat(secrets).allSatisfy(secret -> assertThat(event.toString()).doesNotContain(secret));
  }

  @Test
  void fixesStructuredLoggerLevels() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

    assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.INFO);
    assertThat(context.getLogger("com.sportsbook.admin").getLevel()).isEqualTo(Level.INFO);
    assertThat(context.getLogger("org.apache.kafka").getLevel()).isEqualTo(Level.WARN);
  }

  private static JsonNode event(CapturedOutput output, String marker) throws Exception {
    String line =
        output.getOut().lines().filter(value -> value.contains(marker)).findFirst().orElseThrow();
    return JSON.readTree(line);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
      })
  static class LoggingApplication {}
}
