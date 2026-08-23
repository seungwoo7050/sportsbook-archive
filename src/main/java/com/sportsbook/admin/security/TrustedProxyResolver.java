package com.sportsbook.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

final class TrustedProxyResolver {

  private static final String FORWARDED_FOR = "X-Forwarded-For";
  private final List<CidrBlock> trustedProxies;

  TrustedProxyResolver(List<String> trustedProxyCidrs) {
    trustedProxies = trustedProxyCidrs.stream().map(CidrBlock::parse).toList();
  }

  Optional<InetAddress> resolve(HttpServletRequest request) {
    Optional<InetAddress> parsedPeer = CidrBlock.parseAddress(request.getRemoteAddr());
    if (parsedPeer.isEmpty()) {
      return Optional.empty();
    }
    InetAddress peer = parsedPeer.orElseThrow();
    if (!isTrusted(peer)) {
      return Optional.of(peer);
    }

    List<InetAddress> hops = forwardedHops(request);
    if (hops.isEmpty()) {
      return Optional.empty();
    }
    for (int index = hops.size() - 1; index >= 0; index--) {
      InetAddress hop = hops.get(index);
      if (!isTrusted(hop)) {
        return Optional.of(hop);
      }
    }
    return Optional.empty();
  }

  private List<InetAddress> forwardedHops(HttpServletRequest request) {
    List<String> values = Collections.list(request.getHeaders(FORWARDED_FOR));
    List<InetAddress> hops = new ArrayList<>();
    for (String value : values) {
      for (String hop : value.split(",", -1)) {
        Optional<InetAddress> parsed = CidrBlock.parseAddress(hop);
        if (parsed.isEmpty()) {
          return List.of();
        }
        hops.add(parsed.orElseThrow());
      }
    }
    return List.copyOf(hops);
  }

  private boolean isTrusted(InetAddress address) {
    return trustedProxies.stream().anyMatch(cidr -> cidr.contains(address));
  }
}
