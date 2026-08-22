package com.sportsbook.settlement.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class AdminAuthenticationFilter extends OncePerRequestFilter {

  private static final String ADMIN_PREFIX = "/internal/admin";

  private final byte[] expectedSecret;
  private final AdminProblemWriter problems;

  public AdminAuthenticationFilter(AdminCredentials credentials, AdminProblemWriter problems) {
    this.expectedSecret = credentials.apiKey().getBytes(StandardCharsets.UTF_8);
    this.problems = problems;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.equals(ADMIN_PREFIX) && !path.startsWith(ADMIN_PREFIX + "/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    List<String> callers = Collections.list(request.getHeaders(AdminCredentials.SERVICE_HEADER));
    List<String> secrets = Collections.list(request.getHeaders(AdminCredentials.API_KEY_HEADER));
    if (callers.isEmpty() || secrets.isEmpty()) {
      problems.write(request, response, HttpStatus.UNAUTHORIZED, "Admin credentials are required");
      return;
    }
    if (callers.size() != 1 || secrets.size() != 1) {
      problems.write(request, response, HttpStatus.FORBIDDEN, "Admin credentials are invalid");
      return;
    }
    String caller = callers.get(0);
    String supplied = secrets.get(0);
    boolean callerMatches = AdminCredentials.CALLER.equals(caller);
    boolean secretMatches =
        MessageDigest.isEqual(expectedSecret, supplied.getBytes(StandardCharsets.UTF_8));
    if (!callerMatches || !secretMatches) {
      problems.write(request, response, HttpStatus.FORBIDDEN, "Admin credentials are invalid");
      return;
    }
    chain.doFilter(request, response);
  }
}
