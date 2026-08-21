package com.sportsbook.gateway.ratelimit;

import com.sportsbook.gateway.error.GatewayErrorCode;
import com.sportsbook.gateway.error.GatewayProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RateLimitFilter extends OncePerRequestFilter {

  static final String REMAINING_HEADER = "X-RateLimit-Remaining";

  private final RateLimitProperties properties;
  private final RateLimitKeyResolver keys;
  private final RateLimiterService limiter;
  private final GatewayProblemWriter problems;

  public RateLimitFilter(
      RateLimitProperties properties,
      RateLimitKeyResolver keys,
      RateLimiterService limiter,
      GatewayProblemWriter problems) {
    this.properties = properties;
    this.keys = keys;
    this.limiter = limiter;
    this.problems = problems;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !properties.enabled()
        || path.equals("/error")
        || path.equals("/actuator")
        || path.startsWith("/actuator/");
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return true;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    RateLimitKeyResolver.ResolvedKey key = keys.resolve(request);
    RateLimiterService.Result result = limiter.tryConsume(key.value(), key.limit());
    if (result.allowed()) {
      if (!result.failOpen()) {
        response.setHeader(REMAINING_HEADER, Long.toString(result.remainingTokens()));
      }
      chain.doFilter(request, response);
      return;
    }

    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(result.retryAfterSeconds()));
    response.setHeader(REMAINING_HEADER, "0");
    problems.write(request, response, GatewayErrorCode.GATEWAY_RATE_LIMITED);
  }
}
