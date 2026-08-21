package com.sportsbook.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class TrustedHeaderFilter extends OncePerRequestFilter {

  private static final List<String> HIDDEN =
      List.of(
          GatewayHeaders.USER_ID,
          GatewayHeaders.USER_ROLES,
          GatewayHeaders.INTERNAL_SERVICE,
          GatewayHeaders.INTERNAL_API_KEY);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(new TrustedHeaderRequest(request), response);
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  @Override
  protected void doFilterNestedErrorDispatch(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    doFilterInternal(request, response, chain);
  }

  private static boolean hidden(String name) {
    return name != null && HIDDEN.stream().anyMatch(name::equalsIgnoreCase);
  }

  private static final class TrustedHeaderRequest extends HttpServletRequestWrapper {

    private TrustedHeaderRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getHeader(String name) {
      return hidden(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return hidden(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Enumeration<String> names = super.getHeaderNames();
      if (names == null) {
        return null;
      }
      return Collections.enumeration(
          Collections.list(names).stream().filter(name -> !hidden(name)).toList());
    }
  }
}
