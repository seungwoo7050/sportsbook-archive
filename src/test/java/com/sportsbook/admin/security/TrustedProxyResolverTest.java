package com.sportsbook.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedProxyResolverTest {

  private final TrustedProxyResolver resolver =
      new TrustedProxyResolver(List.of("10.0.0.0/8", "fd00::/8"));

  @Test
  void ignoresForwardedHeadersFromAnUntrustedPeer() throws Exception {
    MockHttpServletRequest request = requestFrom("203.0.113.9");
    request.addHeader("X-Forwarded-For", "10.1.2.3");

    assertThat(resolver.resolve(request)).contains(InetAddress.getByName("203.0.113.9"));
  }

  @Test
  void walksATrustedProxyChainFromRightToLeft() throws Exception {
    MockHttpServletRequest request = requestFrom("10.0.0.5");
    request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.4");

    assertThat(resolver.resolve(request)).contains(InetAddress.getByName("198.51.100.7"));
  }

  @Test
  void rejectsATrustedPeerWhenAnyForwardedHopIsMalformed() {
    MockHttpServletRequest request = requestFrom("10.0.0.5");
    request.addHeader("X-Forwarded-For", "198.51.100.7, attacker.example");

    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void rejectsAChainWithNoResolvableClient() {
    MockHttpServletRequest request = requestFrom("10.0.0.5");
    request.addHeader("X-Forwarded-For", "10.0.0.2, 10.0.0.4");

    assertThat(resolver.resolve(request)).isEmpty();
    assertThat(resolver.resolve(requestFrom("10.0.0.5"))).isEmpty();
  }

  @Test
  void supportsTrustedIpv6ProxiesAndRejectsANonnumericPeer() throws Exception {
    MockHttpServletRequest ipv6 = requestFrom("fd00::5");
    ipv6.addHeader("X-Forwarded-For", "2001:db8::42");

    assertThat(resolver.resolve(ipv6)).contains(InetAddress.getByName("2001:db8::42"));
    assertThat(resolver.resolve(requestFrom("proxy.internal"))).isEmpty();
  }

  private static MockHttpServletRequest requestFrom(String remoteAddress) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/v1/probe");
    request.setRemoteAddr(remoteAddress);
    return request;
  }
}
