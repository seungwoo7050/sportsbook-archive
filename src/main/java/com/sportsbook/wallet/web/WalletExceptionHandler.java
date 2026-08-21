package com.sportsbook.wallet.web;

import com.sportsbook.wallet.domain.error.AccountNotFoundException;
import com.sportsbook.wallet.domain.error.CurrencyMismatchException;
import com.sportsbook.wallet.domain.error.IdempotencyConflictException;
import com.sportsbook.wallet.domain.error.WalletAccessDeniedException;
import com.sportsbook.wallet.domain.error.WalletAdjustmentNotFoundException;
import com.sportsbook.wallet.domain.error.WalletBusyException;
import com.sportsbook.wallet.domain.error.WalletOperationNotFoundException;
import com.sportsbook.wallet.domain.error.WalletRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

  @ExceptionHandler(AccountNotFoundException.class)
  ProblemDetail accountNotFound(AccountNotFoundException exception, HttpServletRequest request) {
    return atRequest(
        WalletProblems.from(
            WalletError.ACCOUNT_NOT_FOUND, "The requested wallet account does not exist"),
        request);
  }

  @ExceptionHandler(WalletOperationNotFoundException.class)
  ProblemDetail operationNotFound(
      WalletOperationNotFoundException exception, HttpServletRequest request) {
    return atRequest(
        WalletProblems.from(
            WalletError.OPERATION_NOT_FOUND, "The requested wallet operation does not exist"),
        request);
  }

  @ExceptionHandler(WalletAdjustmentNotFoundException.class)
  ProblemDetail adjustmentNotFound(
      WalletAdjustmentNotFoundException exception, HttpServletRequest request) {
    return atRequest(
        WalletProblems.from(
            WalletError.ADJUSTMENT_NOT_FOUND, "The requested wallet adjustment does not exist"),
        request);
  }

  @ExceptionHandler(CurrencyMismatchException.class)
  ProblemDetail currencyMismatch(CurrencyMismatchException exception, HttpServletRequest request) {
    ProblemDetail problem =
        WalletProblems.from(
            WalletError.CURRENCY_MISMATCH,
            "The requested currency does not match the wallet account");
    problem.setProperty("expectedCurrency", exception.expected());
    return atRequest(problem, request);
  }

  @ExceptionHandler(WalletAccessDeniedException.class)
  ProblemDetail accessDenied(WalletAccessDeniedException exception, HttpServletRequest request) {
    return atRequest(
        WalletProblems.from(
            WalletError.ACCESS_DENIED, "Authenticated caller cannot perform this wallet operation"),
        request);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    HttpMessageNotReadableException.class,
    HttpMediaTypeNotSupportedException.class,
    MethodArgumentTypeMismatchException.class,
    ServletRequestBindingException.class,
    IllegalArgumentException.class
  })
  ProblemDetail invalidRequest(Exception exception, HttpServletRequest request) {
    return atRequest(
        WalletProblems.from(
            WalletError.INVALID_REQUEST,
            "Wallet request is malformed or violates validation constraints"),
        request);
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail internalError(Exception exception, HttpServletRequest request) {
    return atRequest(
        WalletProblems.from(WalletError.INTERNAL_ERROR, "Wallet request could not be completed"),
        request);
  }

  private ProblemDetail atRequest(ProblemDetail problem, HttpServletRequest request) {
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }
}
