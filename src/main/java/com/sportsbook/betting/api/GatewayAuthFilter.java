package com.sportsbook.betting.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
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
    String path = businessPath(request);
    return !path.startsWith("/internal/") && !path.startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    List<String> callers = Collections.list(request.getHeaders(SERVICE_HEADER));
    List<String> keys = Collections.list(request.getHeaders(API_KEY_HEADER));
    boolean exactKey = keys.size() == 1 && credentials.matches(keys.get(0));
    if (!exactKey || callers.size() != 1) {
      reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED");
      return;
    }
    boolean trustedCaller = "gateway".equals(callers.get(0));
    String path = businessPath(request);
    String method = request.getMethod();
    boolean collection = path.equals("/internal/v1/bets");
    boolean item =
        path.matches(
            "/internal/v1/bets/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    boolean allowedRoute =
        (collection && (method.equals("GET") || method.equals("POST")))
            || (item && method.equals("GET"));
    if (!trustedCaller || !allowedRoute) {
      reject(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN");
      return;
    }
    chain.doFilter(request, response);
  }

  private static String businessPath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String context = request.getContextPath();
    return context.isEmpty() || !uri.startsWith(context) ? uri : uri.substring(context.length());
  }

  private static void reject(HttpServletResponse response, int status, String code)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response
        .getWriter()
        .write(
            "{\"type\":\"https://sportsbook/errors/"
                + code.toLowerCase()
                + "\","
                + "\"title\":\""
                + code
                + "\",\"status\":"
                + status
                + ",\"errorCode\":\""
                + code
                + "\"}");
  }
}
