package com.sportsbook.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class WalletSecurityFailureHandlerTest {
  private static final String SECRET = "never-echo-this-secret-credential-value";

  private final ObjectMapper objectMapper =
      new ObjectMapper().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
  private final WalletSecurityFailureHandler handler =
      new WalletSecurityFailureHandler(objectMapper);

  @Test
  void writesAuthenticationProblemsWithoutEchoingSecrets() throws Exception {
    MockHttpServletRequest request = request("/internal/v1/wallet/accounts");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.commence(request, response, new BadCredentialsException("invalid " + SECRET));

    JsonNode body = body(response);
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(body.path("type").asText())
        .isEqualTo("https://sportsbook/errors/wallet/authentication-required");
    assertThat(body.path("title").asText()).isEqualTo("Authentication required");
    assertThat(body.path("status").asInt()).isEqualTo(401);
    assertThat(body.path("detail").asText())
        .isEqualTo(WalletSecurityFailureHandler.AUTHENTICATION_DETAIL);
    assertThat(body.path("instance").asText()).isEqualTo("/internal/v1/wallet/accounts");
    assertThat(body.path("errorCode").asText()).isEqualTo("WALLET_AUTHENTICATION_REQUIRED");
    assertThat(response.getContentAsString()).doesNotContain(SECRET, "invalid");
  }

  @Test
  void writesAccessDeniedProblemsWithoutEchoingExceptions() throws Exception {
    MockHttpServletRequest request = request("/internal/v1/wallet/balance");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.handle(request, response, new AccessDeniedException("denied " + SECRET));

    JsonNode body = body(response);
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(body.path("type").asText())
        .isEqualTo("https://sportsbook/errors/wallet/access-denied");
    assertThat(body.path("title").asText()).isEqualTo("Wallet access denied");
    assertThat(body.path("status").asInt()).isEqualTo(403);
    assertThat(body.path("detail").asText()).isEqualTo(WalletSecurityFailureHandler.ACCESS_DETAIL);
    assertThat(body.path("instance").asText()).isEqualTo("/internal/v1/wallet/balance");
    assertThat(body.path("errorCode").asText()).isEqualTo("WALLET_ACCESS_DENIED");
    assertThat(response.getContentAsString()).doesNotContain(SECRET, "denied " + SECRET);
  }

  @Test
  void rejectsMissingObjectMapper() {
    assertThatNullPointerException().isThrownBy(() -> new WalletSecurityFailureHandler(null));
  }

  private MockHttpServletRequest request(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.addHeader("X-Internal-Api-Key", SECRET);
    return request;
  }

  private JsonNode body(MockHttpServletResponse response) throws Exception {
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
    return objectMapper.readTree(response.getContentAsByteArray());
  }
}
