package com.sportsbook.admin.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public final class AdminMutationContextFilter extends OncePerRequestFilter {

  private static final Set<String> MUTATIONS = Set.of("POST", "PATCH", "DELETE");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/admin/v1/")
        || !MUTATIONS.contains(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication()
            instanceof JwtAuthenticationToken authentication
        && authentication.isAuthenticated()) {
      AdminContextArgumentResolver.initialize(request, response);
    }
    chain.doFilter(request, response);
  }
}
