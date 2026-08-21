package com.sportsbook.protocol.error;

import java.net.URI;

/** Stable error vocabulary shared by sportsbook service boundaries. */
public enum ErrorCode {
  VALIDATION_FAILED(400, "validation-failed", "Validation failed"),
  ODDS_DRIFT(409, "odds-drift", "Odds drift exceeds tolerance"),
  DUPLICATE_BET(409, "duplicate-bet", "Duplicate bet (idempotency violation)"),
  INSUFFICIENT_BALANCE(409, "insufficient-balance", "Insufficient wallet balance"),
  LIMIT_EXCEEDED(403, "limit-exceeded", "User or market limit exceeded"),
  EVENT_CLOSED(422, "event-closed", "Event is closed for betting"),
  SERVICE_UNAVAILABLE(503, "service-unavailable", "Dependent service unavailable"),
  INTERNAL_ERROR(500, "internal-error", "Internal server error");

  private static final String TYPE_BASE = "https://sportsbook/errors/";

  private final int httpStatus;
  private final URI type;
  private final String title;

  ErrorCode(int httpStatus, String type, String title) {
    this.httpStatus = httpStatus;
    this.type = URI.create(TYPE_BASE + type);
    this.title = title;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public URI type() {
    return type;
  }

  public String title() {
    return title;
  }
}
