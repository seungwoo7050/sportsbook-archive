package com.sportsbook.wallet.security;

import com.sportsbook.wallet.domain.WalletCaller;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates one unambiguous internal caller header pair without retaining its API key. */
public final class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {
  public static final String SERVICE_HEADER = "X-Internal-Service";
  public static final String API_KEY_HEADER = "X-Internal-Api-Key";

  private final WalletCredentials credentials;
  private final WalletSecurityFailureHandler failureHandler;

  public InternalApiKeyAuthenticationFilter(
      WalletCredentials credentials, WalletSecurityFailureHandler failureHandler) {
    this.credentials = Objects.requireNonNull(credentials, "credentials");
    this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    List<String> callers = headers(request, SERVICE_HEADER);
    List<String> apiKeys = headers(request, API_KEY_HEADER);
    if (callers.isEmpty() && apiKeys.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }
    if (callers.size() != 1 || apiKeys.size() != 1) {
      reject(request, response);
      return;
    }

    Optional<WalletCaller> caller = credentials.authenticate(callers.get(0), apiKeys.get(0));
    if (caller.isEmpty()) {
      reject(request, response);
      return;
    }

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(caller.orElseThrow(), null, List.of()));
    SecurityContextHolder.setContext(context);
    filterChain.doFilter(request, response);
  }

  private List<String> headers(HttpServletRequest request, String name) {
    return Collections.list(request.getHeaders(name));
  }

  private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
    SecurityContextHolder.clearContext();
    failureHandler.authenticationRequired(request, response);
  }
}
