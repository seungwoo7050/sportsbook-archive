package com.sportsbook.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayProblemWriterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void writesStableProblemContractWithoutRequestSecrets() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bets");
    request.addHeader("Authorization", "Bearer must-not-leak");
    MockHttpServletResponse response = new MockHttpServletResponse();
    DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
    GatewayProblemWriter writer =
        new GatewayProblemWriter(objectMapper, beans.getBeanProvider(Tracer.class));

    writer.write(request, response, GatewayErrorCode.GATEWAY_UNAUTHORIZED);

    JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(body.get("type").asText()).isEqualTo("https://sportsbook/errors/unauthorized");
    assertThat(body.get("title").asText()).isEqualTo("Unauthorized");
    assertThat(body.get("status").asInt()).isEqualTo(401);
    assertThat(body.get("errorCode").asText()).isEqualTo("GATEWAY_UNAUTHORIZED");
    assertThat(body.get("detail").asText()).isEqualTo("Authentication is required.");
    assertThat(body.get("instance").asText()).isEqualTo("/api/v1/bets");
    assertThat(UUID.fromString(body.get("correlationId").asText())).isNotNull();
    assertThat(response.getContentAsString()).doesNotContain("must-not-leak");
  }
}
