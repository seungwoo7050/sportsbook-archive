package com.sportsbook.gateway.logging;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StructuredLoggingTest {

  private static final Logger log = LoggerFactory.getLogger(StructuredLoggingTest.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void redactsSecretsAndEmitsOnlyAllowedContext(CapturedOutput output) throws Exception {
    List<String> secrets =
        List.of(
            "basic.fixture.secret",
            "internal.fixture.secret",
            "api.fixture.secret",
            "password.fixture.secret",
            "token.fixture.secret",
            "bare.fixture.secret",
            "mdc.fixture.secret",
            "stack.fixture.secret");
    MDC.put("traceId", "fixture-trace-id");
    MDC.put("spanId", "fixture-span-id");
    MDC.put("authorization", secrets.get(6));
    try {
      log.info(
          "audit-marker authorization: Basic {}, x-internal-api-key={}, apiKey={}, password={},"
              + " token={}, standalone Bearer {}",
          secrets.get(0),
          secrets.get(1),
          secrets.get(2),
          secrets.get(3),
          secrets.get(4),
          secrets.get(5),
          new IllegalStateException("authorization: Basic " + secrets.get(7)));
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
            "service");
    assertThat(event.path("service").asText()).isEqualTo("gateway");
    assertThat(event.path("traceId").asText()).isEqualTo("fixture-trace-id");
    assertThat(event.path("spanId").asText()).isEqualTo("fixture-span-id");
    assertThat(event.has("authorization")).isFalse();
    assertThat(event.path("message").asText()).contains("[REDACTED]");
    assertThat(event.path("stack_trace").asText()).contains("IllegalStateException", "[REDACTED]");
    assertThat(secrets).allSatisfy(secret -> assertThat(event.toString()).doesNotContain(secret));
  }

  @Test
  void omitsStackTraceWithoutFailure(CapturedOutput output) throws Exception {
    log.info("plain-marker");

    assertThat(event(output, "plain-marker").has("stack_trace")).isFalse();
  }

  @Test
  void fixesApplicationAndDependencyLogLevels() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

    assertThat(context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getLevel())
        .isEqualTo(Level.INFO);
    assertThat(context.getLogger("com.sportsbook.gateway").getLevel()).isEqualTo(Level.INFO);
    assertThat(context.getLogger("org.apache.kafka").getLevel()).isEqualTo(Level.WARN);
  }

  private static JsonNode event(CapturedOutput output, String marker) throws Exception {
    String line =
        output.getOut().lines().filter(value -> value.contains(marker)).findFirst().orElseThrow();
    return JSON.readTree(line);
  }
}
