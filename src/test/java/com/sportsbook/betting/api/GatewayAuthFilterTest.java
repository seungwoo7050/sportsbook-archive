package com.sportsbook.betting.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.betting.client.ClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayAuthFilterTest {

  private static final String SECRET = "a".repeat(32);

  @Test
  void failsStartupForMissingStrengthAndNeverRendersTheSecret() {
    assertThatThrownBy(() -> new GatewayAuthProperties("short"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GatewayAuthProperties(" ".repeat(32)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(new GatewayAuthProperties(SECRET).toString())
        .doesNotContain(SECRET)
        .contains("redacted");
  }

  @Test
  void failsStartupWhenGatewayReusesADependencyKey() {
    ClientProperties clients =
        new ClientProperties(
            "http://risk", "http://wallet", null, null, "r".repeat(32), "w".repeat(32));

    assertThatThrownBy(() -> new GatewayAuthProperties("r".repeat(32), clients))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("distinct");
    assertThatThrownBy(() -> new GatewayAuthProperties("w".repeat(32), clients))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("distinct");
  }

  @Test
  void permitsOnlyTheExactGatewayCallerAndSecret() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/bets");
    request.addHeader(GatewayAuthFilter.SERVICE_HEADER, "gateway");
    request.addHeader(GatewayAuthFilter.API_KEY_HEADER, SECRET);
    MockHttpServletResponse response = new MockHttpServletResponse();

    new GatewayAuthFilter(new GatewayAuthProperties(SECRET))
        .doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void rejectsSpoofedOrMismatchedInternalHeaders() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/bets");
    request.addHeader(GatewayAuthFilter.SERVICE_HEADER, "client");
    request.addHeader(GatewayAuthFilter.API_KEY_HEADER, SECRET);
    MockHttpServletResponse response = new MockHttpServletResponse();

    new GatewayAuthFilter(new GatewayAuthProperties(SECRET))
        .doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("FORBIDDEN");
  }

  @Test
  void returnsUnauthorizedForMissingOrDuplicateCredentials() throws Exception {
    MockHttpServletRequest missing = new MockHttpServletRequest("GET", "/internal/v1/bets");
    missing.addHeader(GatewayAuthFilter.SERVICE_HEADER, "gateway");
    MockHttpServletResponse missingResponse = new MockHttpServletResponse();
    GatewayAuthFilter filter = new GatewayAuthFilter(new GatewayAuthProperties(SECRET));

    filter.doFilter(missing, missingResponse, new MockFilterChain());

    assertThat(missingResponse.getStatus()).isEqualTo(401);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/bets");
    request.addHeader(GatewayAuthFilter.SERVICE_HEADER, "gateway");
    request.addHeader(GatewayAuthFilter.SERVICE_HEADER, "gateway");
    request.addHeader(GatewayAuthFilter.API_KEY_HEADER, SECRET);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
  }

  @Test
  void deniesUnlistedBusinessRoutesEvenForTheGateway() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/v1/admin/reprice");
    request.addHeader(GatewayAuthFilter.SERVICE_HEADER, "gateway");
    request.addHeader(GatewayAuthFilter.API_KEY_HEADER, SECRET);
    MockHttpServletResponse response = new MockHttpServletResponse();

    new GatewayAuthFilter(new GatewayAuthProperties(SECRET))
        .doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void deniesWrongMethodsAndNestedPathsBeforeControllerMapping() throws Exception {
    GatewayAuthFilter filter = new GatewayAuthFilter(new GatewayAuthProperties(SECRET));
    MockHttpServletRequest wrongMethod = authenticated("PUT", "/internal/v1/bets");
    MockHttpServletResponse wrongMethodResponse = new MockHttpServletResponse();
    MockHttpServletRequest nested = authenticated("GET", "/internal/v1/bets/one/export");
    MockHttpServletResponse nestedResponse = new MockHttpServletResponse();

    filter.doFilter(wrongMethod, wrongMethodResponse, new MockFilterChain());
    filter.doFilter(nested, nestedResponse, new MockFilterChain());

    assertThat(wrongMethodResponse.getStatus()).isEqualTo(403);
    assertThat(nestedResponse.getStatus()).isEqualTo(403);
  }

  @Test
  void matchesRoutesBelowTheConfiguredContextPath() throws Exception {
    MockHttpServletRequest request = authenticated("GET", "/sportsbook/internal/v1/bets");
    request.setContextPath("/sportsbook");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new GatewayAuthFilter(new GatewayAuthProperties(SECRET))
        .doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(200);
  }

  private static MockHttpServletRequest authenticated(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.addHeader(GatewayAuthFilter.SERVICE_HEADER, "gateway");
    request.addHeader(GatewayAuthFilter.API_KEY_HEADER, SECRET);
    return request;
  }
}
