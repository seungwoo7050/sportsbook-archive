package com.sportsbook.oddsfeed.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.oddsfeed.config.InternalSecurityProperties;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalApiKeyAuthenticationFilterTest {

  private static final String SECRET = "0123456789abcdef0123456789abcdef";

  private final InternalApiKeyAuthenticationFilter filter =
      new InternalApiKeyAuthenticationFilter(new InternalSecurityProperties(SECRET));

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @MethodSource("invalidCredentials")
  void rejectsMissingOrInvalidCredentials(String service, String key) throws Exception {
    MockHttpServletRequest request = internalRequest(service, key);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void authenticatesNonAdminCallerWithoutAuthority() throws Exception {
    filter.doFilter(
        internalRequest("settlement-service", SECRET),
        new MockHttpServletResponse(),
        new MockFilterChain());

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getName()).isEqualTo("settlement-service");
    assertThat(authentication.getAuthorities()).isEmpty();
  }

  @Test
  void grantsOnlyTheAdminCallerAuthority() throws Exception {
    filter.doFilter(
        internalRequest("admin-api", SECRET), new MockHttpServletResponse(), new MockFilterChain());

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getName()).isEqualTo("admin-api");
    assertThat(authentication.getAuthorities())
        .extracting("authority")
        .containsExactly(InternalApiKeyAuthenticationFilter.AUTHORITY);
  }

  private static Stream<Arguments> invalidCredentials() {
    return Stream.of(
        Arguments.of(null, SECRET),
        Arguments.of("", SECRET),
        Arguments.of("admin-api", null),
        Arguments.of("admin-api", "wrong-key"));
  }

  private static MockHttpServletRequest internalRequest(String service, String key) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/action");
    if (service != null) {
      request.addHeader(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, service);
    }
    if (key != null) {
      request.addHeader(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, key);
    }
    return request;
  }
}
