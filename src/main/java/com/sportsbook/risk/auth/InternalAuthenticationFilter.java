package com.sportsbook.risk.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.error.ProblemDetail;
import com.sportsbook.risk.auth.InternalAuthProperties.Caller;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates internal callers with a caller-specific API key. */
@Component
public class InternalAuthenticationFilter extends OncePerRequestFilter {
  public static final String SERVICE_HEADER = "X-Internal-Service";
  public static final String API_KEY_HEADER = "X-Internal-Api-Key";

  private final InternalAuthProperties properties;
  private final ObjectMapper mapper;

  public InternalAuthenticationFilter(InternalAuthProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Caller caller = Caller.fromWire(request.getHeader(SERVICE_HEADER)).orElse(null);
    if (caller == null || !properties.matches(caller, request.getHeader(API_KEY_HEADER))) {
      unauthorized(request, response);
      return;
    }
    var authority = new SimpleGrantedAuthority("ROLE_" + caller.name());
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            caller.wireName(), null, List.of(authority));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    request.setAttribute(Caller.class.getName(), caller);
    chain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.equals("/actuator/health")
        || path.startsWith("/actuator/health/")
        || path.equals("/actuator/prometheus")) {
      return true;
    }
    return !path.startsWith("/internal/") && !path.startsWith("/actuator/");
  }

  private void unauthorized(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    ProblemDetail problem =
        new ProblemDetail(
            URI.create("https://sportsbook/errors/unauthorized"),
            "Unauthorized",
            HttpServletResponse.SC_UNAUTHORIZED,
            "UNAUTHORIZED",
            "Missing or invalid internal credentials",
            URI.create(request.getRequestURI()),
            null);
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), problem);
  }
}
