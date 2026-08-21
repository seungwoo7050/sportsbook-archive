package com.sportsbook.wallet.web;

import com.sportsbook.wallet.domain.error.IdempotencyConflictException;
import com.sportsbook.wallet.domain.error.WalletBusyException;
import com.sportsbook.wallet.domain.error.WalletRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps durable and retryable wallet failures without exposing request credentials. */
@RestControllerAdvice
public class WalletExceptionHandler {

  @ExceptionHandler(WalletRejectedException.class)
  ProblemDetail rejected(WalletRejectedException exception, HttpServletRequest request) {
    return atRequest(WalletProblems.from(exception.failure()), request);
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  ProblemDetail idempotencyConflict(
      IdempotencyConflictException exception, HttpServletRequest request) {
    return atRequest(
        WalletProblems.from(
            WalletError.IDEMPOTENCY_CONFLICT,
            "Idempotency key belongs to a different wallet request"),
        request);
  }

  @ExceptionHandler(WalletBusyException.class)
  ResponseEntity<ProblemDetail> busy(WalletBusyException exception, HttpServletRequest request) {
    ProblemDetail problem =
        atRequest(
            WalletProblems.from(
                WalletError.WALLET_BUSY, "Retry the wallet request after one second"),
            request);
    return ResponseEntity.status(WalletError.WALLET_BUSY.httpStatus())
        .header(HttpHeaders.RETRY_AFTER, "1")
        .body(problem);
  }

  private ProblemDetail atRequest(ProblemDetail problem, HttpServletRequest request) {
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }
}
