package com.sportsbook.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.wallet.domain.WalletCaller;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalApiKeyAuthenticationRejectionTest {
  private static final String PLATFORM_KEY = "platform:" + "p".repeat(32);

  private final InternalApiKeyAuthenticationFilter filter =
      new InternalApiKeyAuthenticationFilter(
          new WalletCredentials(
              new WalletSecurityProperties(
                  PLATFORM_KEY,
                  "gateway:" + "g".repeat(32),
                  "betting:" + "b".repeat(32),
                  "settlement:" + "s".repeat(32),
                  "admin:" + "a".repeat(32))),
          new WalletSecurityFailureHandler(
              new ObjectMapper().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)));

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rejectsPartialDuplicateAndInvalidCredentials() throws Exception {
    assertRejected(withHeader(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, "platform"));
    assertRejected(withHeader(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, PLATFORM_KEY));
    assertRejected(duplicate(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, "platform"));
    assertRejected(duplicate(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, PLATFORM_KEY));
    assertRejected(pair("platform", "invalid"));
    assertRejected(pair(" ", PLATFORM_KEY));
    assertRejected(pair("platform", " "));
    assertRejected(pair("gateway", PLATFORM_KEY));
    assertRejected(pair("unknown", PLATFORM_KEY));
  }

  @Test
  void clearsPreexistingAuthenticationWhenCredentialsAreInvalid() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(WalletCaller.ADMIN, null, List.of()));

    assertRejected(pair("platform", "invalid"));
  }

  private void assertRejected(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean invoked = new AtomicBoolean();
    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));
    assertThat(invoked).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("WALLET_AUTHENTICATION_REQUIRED");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private MockHttpServletRequest pair(String caller, String key) {
    MockHttpServletRequest request =
        withHeader(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, caller);
    request.addHeader(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, key);
    return request;
  }

  private MockHttpServletRequest duplicate(String name, String value) {
    MockHttpServletRequest request = withHeader(name, value);
    request.addHeader(name, value);
    if (name.equals(InternalApiKeyAuthenticationFilter.SERVICE_HEADER)) {
      request.addHeader(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, PLATFORM_KEY);
    } else {
      request.addHeader(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, "platform");
    }
    return request;
  }

  private MockHttpServletRequest withHeader(String name, String value) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/internal/v1/wallet/balance");
    request.addHeader(name, value);
    return request;
  }
}
