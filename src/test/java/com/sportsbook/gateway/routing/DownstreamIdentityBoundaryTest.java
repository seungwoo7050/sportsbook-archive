package com.sportsbook.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.gateway.security.GatewayHeaders;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

class DownstreamIdentityBoundaryTest {

  private static final String USER_ID = "11111111-1111-4111-8111-111111111111";

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void removesCallerCredentialsBeforeProxying() {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer external-token");
    servletRequest.addHeader(GatewayHeaders.USER_ID, "spoofed-user");
    servletRequest.addHeader(GatewayHeaders.USER_ROLES, "ADMIN");
    servletRequest.addHeader(GatewayHeaders.INTERNAL_SERVICE, "spoofed-service");
    servletRequest.addHeader(GatewayHeaders.INTERNAL_API_KEY, "spoofed-key");
    servletRequest.addHeader("traceparent", "00-trace-span-01");

    ServerRequest sanitized = new DownstreamRequestSanitizer().apply(request(servletRequest));

    assertThat(sanitized.headers().firstHeader(HttpHeaders.AUTHORIZATION)).isNull();
    assertThat(sanitized.headers().firstHeader(GatewayHeaders.USER_ID)).isNull();
    assertThat(sanitized.headers().firstHeader(GatewayHeaders.USER_ROLES)).isNull();
    assertThat(sanitized.headers().firstHeader(GatewayHeaders.INTERNAL_SERVICE)).isNull();
    assertThat(sanitized.headers().firstHeader(GatewayHeaders.INTERNAL_API_KEY)).isNull();
    assertThat(sanitized.headers().firstHeader("traceparent")).isEqualTo("00-trace-span-01");
  }

  @Test
  void forwardsOnlyIdentityDerivedFromTheVerifiedJwt() {
    Jwt jwt =
        Jwt.withTokenValue("verified-token")
            .header("alg", "RS256")
            .subject(USER_ID)
            .claim("roles", List.of("USER", "TRADER"))
            .issuedAt(Instant.now().minusSeconds(1))
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

    ServerRequest forwarded =
        new IdentityForwarding()
            .apply(new DownstreamRequestSanitizer().apply(request(new MockHttpServletRequest())));

    assertThat(forwarded.headers().firstHeader(GatewayHeaders.USER_ID)).isEqualTo(USER_ID);
    assertThat(forwarded.headers().firstHeader(GatewayHeaders.USER_ROLES)).isEqualTo("USER,TRADER");
    assertThat(new IdentityForwarding().currentSubject()).contains(USER_ID);
  }

  private static ServerRequest request(MockHttpServletRequest servletRequest) {
    return ServerRequest.create(servletRequest, List.of());
  }
}
