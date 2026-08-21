package com.sportsbook.risk.api;

import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.sportsbook.protocol.error.ErrorCode;
import com.sportsbook.protocol.error.ProblemDetail;
import com.sportsbook.protocol.value.BetId;
import java.net.URI;

/** Stable lifecycle failures raised at the reservation HTTP boundary. */
final class RiskApiException extends RuntimeException {
  private final int status;
  private final URI type;
  private final String title;
  private final String errorCode;

  private RiskApiException(int status, URI type, String title, String errorCode, String detail) {
    super(detail);
    this.status = status;
    this.type = type;
    this.title = title;
    this.errorCode = errorCode;
  }

  static RiskApiException duplicate(BetId betId) {
    ErrorCode code = ErrorCode.DUPLICATE_BET;
    return new RiskApiException(
        code.httpStatus(),
        code.type(),
        code.title(),
        code.name(),
        "Conflicting reservation " + betId.value());
  }

  static RiskApiException validation(String detail) {
    ErrorCode code = ErrorCode.VALIDATION_FAILED;
    return new RiskApiException(code.httpStatus(), code.type(), code.title(), code.name(), detail);
  }

  static RiskApiException notFound(BetId betId) {
    return custom(
        SC_NOT_FOUND,
        "not-found",
        "Risk reservation not found",
        "RISK_RESERVATION_NOT_FOUND",
        betId);
  }

  static RiskApiException committed(BetId betId) {
    return custom(
        SC_CONFLICT,
        "committed",
        "Risk reservation already committed",
        "RISK_RESERVATION_COMMITTED",
        betId);
  }

  private static RiskApiException custom(
      int status, String suffix, String title, String code, BetId betId) {
    return new RiskApiException(
        status,
        URI.create("https://sportsbook/errors/risk-reservation-" + suffix),
        title,
        code,
        "Reservation " + betId.value() + " cannot complete this transition");
  }

  int status() {
    return status;
  }

  ProblemDetail problem() {
    return new ProblemDetail(type, title, status, errorCode, getMessage(), null, null);
  }
}
