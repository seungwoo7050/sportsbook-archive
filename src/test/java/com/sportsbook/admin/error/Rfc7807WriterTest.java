package com.sportsbook.admin.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class Rfc7807WriterTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final Rfc7807Writer writer = new Rfc7807Writer(objectMapper);

  @AfterEach
  void clearTraceContext() {
    MDC.clear();
  }

  @Test
  void rendersACompleteSecurityProblemWithoutCaching() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/v1/audit-logs");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MDC.put("traceId", "trace-123");

    writer.write(
        request,
        response,
        HttpStatus.UNAUTHORIZED,
        Rfc7807Writer.UNAUTHORIZED,
        "Unauthorized",
        "UNAUTHORIZED",
        "Authentication is required");

    JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(body.path("type").asText()).isEqualTo(Rfc7807Writer.UNAUTHORIZED.toString());
    assertThat(body.path("title").asText()).isEqualTo("Unauthorized");
    assertThat(body.path("status").asInt()).isEqualTo(401);
    assertThat(body.path("errorCode").asText()).isEqualTo("UNAUTHORIZED");
    assertThat(body.path("detail").asText()).isEqualTo("Authentication is required");
    assertThat(body.path("instance").asText()).isEqualTo("/admin/v1/audit-logs");
    assertThat(body.path("correlationId").asText()).isEqualTo("trace-123");
  }
}
