package com.sportsbook.gateway.error;

import java.net.URI;

public enum GatewayErrorCode {
  GATEWAY_UNAUTHORIZED(401, "unauthorized", "Unauthorized", "Authentication is required."),
  GATEWAY_FORBIDDEN(403, "forbidden", "Forbidden", "Access is denied."),
  GATEWAY_RATE_LIMITED(429, "rate-limited", "Too Many Requests", "Request rate limit exceeded."),
  GATEWAY_BAD_GATEWAY(
      502, "upstream-unavailable", "Bad Gateway", "An upstream service is unavailable."),
  GATEWAY_TIMEOUT(504, "upstream-timeout", "Gateway Timeout", "An upstream service timed out.");

  private static final String TYPE_BASE = "https://sportsbook/errors/";

  private final int status;
  private final URI type;
  private final String title;
  private final String detail;

  GatewayErrorCode(int status, String type, String title, String detail) {
    this.status = status;
    this.type = URI.create(TYPE_BASE + type);
    this.title = title;
    this.detail = detail;
  }

  public int status() {
    return status;
  }

  public URI type() {
    return type;
  }

  public String title() {
    return title;
  }

  public String detail() {
    return detail;
  }
}
