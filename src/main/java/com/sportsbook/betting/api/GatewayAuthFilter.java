package com.sportsbook.betting.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

  static final String SERVICE_HEADER = "X-Internal-Service";
  static final String API_KEY_HEADER = "X-Internal-Api-Key";

  private final GatewayAuthProperties credentials;

  public GatewayAuthFilter(GatewayAuthProperties credentials) {
    this.credentials = credentials;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/v1/bets");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean trustedCaller = "gateway".equals(request.getHeader(SERVICE_HEADER));
    boolean trustedKey = credentials.matches(request.getHeader(API_KEY_HEADER));
    if (!trustedCaller || !trustedKey) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response
          .getWriter()
          .write(
              "{\"type\":\"https://sportsbook/errors/forbidden\","
                  + "\"title\":\"Forbidden\",\"status\":403,\"errorCode\":\"FORBIDDEN\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
