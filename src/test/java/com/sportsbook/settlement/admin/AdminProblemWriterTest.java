package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminProblemWriterTest {

  @Test
  void writesOnlyTheSafeRfc9457Fields() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/internal/admin/revisions/x");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new AdminProblemWriter(mapper)
        .write(request, response, HttpStatus.CONFLICT, "The command conflicts with current state");

    JsonNode problem = mapper.readTree(response.getContentAsByteArray());
    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    assertThat(problem.get("title").asText()).isEqualTo("Conflict");
    assertThat(problem.get("detail").asText())
        .isEqualTo("The command conflicts with current state");
    assertThat(problem.get("instance").asText()).isEqualTo("/internal/admin/revisions/x");
    assertThat(response.getContentAsString()).doesNotContain("exception", "sql", "apiKey");
  }
}
