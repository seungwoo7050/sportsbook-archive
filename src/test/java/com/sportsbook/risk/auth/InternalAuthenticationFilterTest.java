package com.sportsbook.risk.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.risk.auth.InternalAuthProperties.Caller;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalAuthenticationFilterTest {
  private static final String BETTING = "b".repeat(32);
  private static final String ADMIN = "a".repeat(32);
  private static final String PLATFORM = "p".repeat(32);

  private final InternalAuthenticationFilter filter =
      new InternalAuthenticationFilter(
          new InternalAuthProperties(BETTING, ADMIN, PLATFORM), new ObjectMapper());

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rejectsMissingUnknownAndInvalidCredentials() throws Exception {
    assertUnauthorized(null, null);
    assertUnauthorized("unknown", BETTING);
    assertUnauthorized("betting-service", ADMIN);
  }

  @Test
  void authenticatesTheMatchingCallerWithoutRetainingTheSecret() throws Exception {
    MockHttpServletRequest request = request("/internal/v1/risk/reservations");
    request.addHeader(InternalAuthenticationFilter.SERVICE_HEADER, "betting-service");
    request.addHeader(InternalAuthenticationFilter.API_KEY_HEADER, BETTING);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<Authentication> observed = new AtomicReference<>();

    filter.doFilter(
        request,
        response,
        (ignoredRequest, ignoredResponse) ->
            observed.set(SecurityContextHolder.getContext().getAuthentication()));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(observed.get().getName()).isEqualTo("betting-service");
    assertThat(observed.get().getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_BETTING_SERVICE");
    assertThat(request.getAttribute(Caller.class.getName())).isEqualTo(Caller.BETTING_SERVICE);
    assertThat(observed.get().getCredentials()).isNull();
  }

  @Test
  void leavesAnonymousHealthRequestsUntouched() throws Exception {
    MockHttpServletRequest request = request("/actuator/health/readiness");
    MockHttpServletResponse response = new MockHttpServletResponse();
    var invoked = new java.util.concurrent.atomic.AtomicBoolean();

    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

    assertThat(invoked).isTrue();
  }

  private void assertUnauthorized(String caller, String secret) throws Exception {
    MockHttpServletRequest request = request("/internal/v1/risk/check");
    if (caller != null) {
      request.addHeader(InternalAuthenticationFilter.SERVICE_HEADER, caller);
    }
    if (secret != null) {
      request.addHeader(InternalAuthenticationFilter.API_KEY_HEADER, secret);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    var invoked = new java.util.concurrent.atomic.AtomicBoolean();

    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    assertThat(response.getContentAsString())
        .contains("UNAUTHORIZED")
        .doesNotContain(BETTING, ADMIN);
    assertThat(invoked).isFalse();
  }

  private static MockHttpServletRequest request(String path) {
    return new MockHttpServletRequest("POST", path);
  }
}
