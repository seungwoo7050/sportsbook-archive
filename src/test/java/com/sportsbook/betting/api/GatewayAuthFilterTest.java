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
}
