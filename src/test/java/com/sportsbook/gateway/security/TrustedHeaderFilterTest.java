package com.sportsbook.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TrustedHeaderFilterTest {

  private static final List<String> TRUST_HEADERS =
      List.of(
          GatewayHeaders.USER_ID,
          GatewayHeaders.USER_ROLES,
          GatewayHeaders.INTERNAL_SERVICE,
          GatewayHeaders.INTERNAL_API_KEY);

  @Test
  void hidesEveryTrustHeaderAcrossServletHeaderApis() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("x-user-id", "spoofed-user");
    request.addHeader("X-USER-ROLES", "ADMIN");
    request.addHeader("x-Internal-Service", "attacker");
    request.addHeader("X-Internal-Api-Key", "first");
    request.addHeader("X-Internal-Api-Key", "second");

    HttpServletRequest filtered = filter(request);

    for (String name : TRUST_HEADERS) {
      assertThat(filtered.getHeader(name)).isNull();
      assertThat(Collections.list(filtered.getHeaders(name.toLowerCase()))).isEmpty();
    }
    assertThat(Collections.list(filtered.getHeaderNames()))
        .noneMatch(name -> TRUST_HEADERS.stream().anyMatch(name::equalsIgnoreCase));
  }

  @Test
  void retainsAuthorizationAndUnrelatedHeaders() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer signed-token");
    request.addHeader("X-Request-Id", "request-1");

    HttpServletRequest filtered = filter(request);

    assertThat(filtered.getHeader("Authorization")).isEqualTo("Bearer signed-token");
    assertThat(filtered.getHeader("X-Request-Id")).isEqualTo("request-1");
    assertThat(Collections.list(filtered.getHeaderNames()))
        .containsExactlyInAnyOrder("Authorization", "X-Request-Id");
  }

  @Test
  void stripsTrustHeadersAgainOnErrorDispatch() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setDispatcherType(DispatcherType.ERROR);
    request.addHeader(GatewayHeaders.INTERNAL_API_KEY, "must-not-survive");

    assertThat(filter(request).getHeader(GatewayHeaders.INTERNAL_API_KEY)).isNull();
  }

  private static HttpServletRequest filter(MockHttpServletRequest request) throws Exception {
    AtomicReference<HttpServletRequest> captured = new AtomicReference<>();
    new TrustedHeaderFilter()
        .doFilter(
            request,
            new MockHttpServletResponse(),
            (filtered, response) -> captured.set((HttpServletRequest) filtered));
    return captured.get();
  }
}
