package com.sportsbook.admin.security;

import com.sportsbook.admin.error.Rfc7807Writer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

final class IpAllowlistFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(IpAllowlistFilter.class);

  private final TrustedProxyResolver clientAddresses;
  private final List<CidrBlock> allowedClients;
  private final Rfc7807Writer problems;

  IpAllowlistFilter(AdminNetworkProperties properties, Rfc7807Writer problems) {
    clientAddresses = new TrustedProxyResolver(properties.trustedProxyCidrs());
    allowedClients = properties.ipAllowlist().stream().map(CidrBlock::parse).toList();
    this.problems = problems;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return !(path.equals("/admin") || path.startsWith("/admin/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<InetAddress> clientAddress = clientAddresses.resolve(request);
    boolean allowed =
        clientAddress.isPresent()
            && allowedClients.stream().anyMatch(cidr -> cidr.contains(clientAddress.orElseThrow()));
    if (allowed) {
      chain.doFilter(request, response);
      return;
    }

    LOG.warn("admin_ip_allowlist_denied path={}", request.getRequestURI());
    problems.write(
        request,
        response,
        HttpStatus.FORBIDDEN,
        Rfc7807Writer.IP_NOT_ALLOWED,
        "Forbidden",
        "IP_NOT_ALLOWED",
        "The client address is not allowed");
  }
}
