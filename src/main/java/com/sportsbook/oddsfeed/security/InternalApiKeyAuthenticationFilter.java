package com.sportsbook.oddsfeed.security;

import com.sportsbook.oddsfeed.config.InternalSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates internal callers using constant-time digest comparisons. */
public class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {

  public static final String SERVICE_HEADER = "X-Internal-Service";
  public static final String API_KEY_HEADER = "X-Internal-Api-Key";
  public static final String EXPECTED_SERVICE = "admin-api";
  public static final String AUTHORITY = "ODDS_INTERNAL_ADMIN";

  private final byte[] expectedServiceDigest;
  private final byte[] expectedApiKeyDigest;

  public InternalApiKeyAuthenticationFilter(InternalSecurityProperties properties) {
    this.expectedServiceDigest = sha256(EXPECTED_SERVICE);
    this.expectedApiKeyDigest = sha256(properties.apiKey());
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String service = request.getHeader(SERVICE_HEADER);
    String apiKey = request.getHeader(API_KEY_HEADER);
    if (service == null || service.isBlank() || !matches(expectedApiKeyDigest, apiKey)) {
      SecurityContextHolder.clearContext();
      response.sendError(HttpStatus.UNAUTHORIZED.value());
      return;
    }

    List<SimpleGrantedAuthority> authorities =
        matches(expectedServiceDigest, service)
            ? List.of(new SimpleGrantedAuthority(AUTHORITY))
            : List.of();
    UsernamePasswordAuthenticationToken authentication =
        UsernamePasswordAuthenticationToken.authenticated(service, null, authorities);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    filterChain.doFilter(request, response);
  }

  private static boolean matches(byte[] expectedDigest, String supplied) {
    return MessageDigest.isEqual(expectedDigest, sha256(supplied == null ? "" : supplied));
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
