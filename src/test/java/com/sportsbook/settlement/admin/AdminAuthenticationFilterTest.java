package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminAuthenticationFilterTest {

  private static final String SECRET = "abcdef0123456789abcdef0123456789";
  private final FilterChain chain = mock(FilterChain.class);
  private final AdminAuthenticationFilter filter =
      new AdminAuthenticationFilter(
          new AdminCredentials(SECRET),
          new AdminProblemWriter(new ObjectMapper().findAndRegisterModules()));

  @Test
  void returnsUnauthorizedWhenEitherCredentialHeaderIsMissing() throws Exception {
    MockHttpServletRequest request = adminRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).doesNotContain(SECRET);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void returnsForbiddenForWrongCallerOrSecret() throws Exception {
    assertForbidden("other-service", SECRET);
    assertForbidden(AdminCredentials.CALLER, "x".repeat(SECRET.length()));
  }

  @Test
  void returnsForbiddenForAmbiguousCredentialHeaders() throws Exception {
    MockHttpServletRequest request = adminRequest();
    request.addHeader(AdminCredentials.SERVICE_HEADER, AdminCredentials.CALLER);
    request.addHeader(AdminCredentials.SERVICE_HEADER, AdminCredentials.CALLER);
    request.addHeader(AdminCredentials.API_KEY_HEADER, SECRET);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void permitsExactCredentialsAndLeavesOtherPathsUnfiltered() throws Exception {
    MockHttpServletRequest admin = adminRequest();
    admin.addHeader(AdminCredentials.SERVICE_HEADER, AdminCredentials.CALLER);
    admin.addHeader(AdminCredentials.API_KEY_HEADER, SECRET);
    MockHttpServletResponse adminResponse = new MockHttpServletResponse();
    MockHttpServletRequest health = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse healthResponse = new MockHttpServletResponse();

    filter.doFilter(admin, adminResponse, chain);
    filter.doFilter(health, healthResponse, chain);

    verify(chain).doFilter(admin, adminResponse);
    verify(chain).doFilter(health, healthResponse);
  }

  private void assertForbidden(String caller, String secret) throws Exception {
    MockHttpServletRequest request = adminRequest();
    request.addHeader(AdminCredentials.SERVICE_HEADER, caller);
    request.addHeader(AdminCredentials.API_KEY_HEADER, secret);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).doesNotContain(SECRET, secret);
    verify(chain, never()).doFilter(request, response);
  }

  private static MockHttpServletRequest adminRequest() {
    return new MockHttpServletRequest("GET", "/internal/admin/revisions/one");
  }
}
