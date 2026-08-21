package com.sportsbook.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitKeyResolverTest {

  private static final RateLimitProperties PROPERTIES =
      new RateLimitProperties(
          true,
          new RateLimitProperties.Limit(120, Duration.ofMinutes(1)),
          new RateLimitProperties.Limit(60, Duration.ofMinutes(1)),
          List.of("10.0.0.0/8"));

  private final RateLimitKeyResolver resolver = new RateLimitKeyResolver(PROPERTIES);

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatedUsersReceiveCanonicalNamespacedBuckets() {
    String subject = "00000000-0000-0000-0000-000000000001";
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(subject, "unused", List.of()));

    RateLimitKeyResolver.ResolvedKey key = resolver.resolve(request("192.0.2.4", "198.51.100.1"));

    assertThat(key.value()).isEqualTo("gateway:ratelimit:user:" + subject);
    assertThat(key.limit().capacity()).isEqualTo(120);
  }

  @Test
  void untrustedPeersCannotSpoofForwardedAddresses() {
    RateLimitKeyResolver.ResolvedKey key = resolver.resolve(request("192.0.2.4", "198.51.100.1"));

    assertThat(key.value()).isEqualTo("gateway:ratelimit:ip:192.0.2.4");
    assertThat(key.limit().capacity()).isEqualTo(60);
  }

  @Test
  void trustedPeersWalkTheForwardedChainFromTheRight() {
    MockHttpServletRequest trusted = request("10.2.3.4", "203.0.113.9, 198.51.100.7, 10.1.2.3");
    assertThat(resolver.resolve(trusted).value()).isEqualTo("gateway:ratelimit:ip:198.51.100.7");

    MockHttpServletRequest malformed = request("10.2.3.4", "198.51.100.7, invalid, 10.1.2.3");
    assertThat(resolver.resolve(malformed).value()).isEqualTo("gateway:ratelimit:ip:10.2.3.4");
  }

  private static MockHttpServletRequest request(String peer, String forwardedFor) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(peer);
    request.addHeader("X-Forwarded-For", forwardedFor);
    return request;
  }
}
