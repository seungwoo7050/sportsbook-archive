package com.sportsbook.betting.api;

import com.sportsbook.betting.error.BetNotFoundException;
import com.sportsbook.betting.error.BetPlacementException;
import com.sportsbook.protocol.error.ErrorCode;
import com.sportsbook.protocol.error.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BetController.class)
public class BetExceptionHandler {

  @ExceptionHandler(BetPlacementException.class)
  ResponseEntity<ProblemDetail> placement(
      BetPlacementException failure, HttpServletRequest request) {
    ErrorCode code = failure.errorCode();
    return ResponseEntity.status(code.httpStatus())
        .body(code.toProblemDetail(failure.getMessage(), instance(request), null));
  }

  @ExceptionHandler(BetNotFoundException.class)
  ResponseEntity<ProblemDetail> missing(BetNotFoundException failure, HttpServletRequest request) {
    ProblemDetail body =
        new ProblemDetail(
            URI.create("https://sportsbook/errors/bet-not-found"),
            "Bet not found",
            404,
            "BET_NOT_FOUND",
            failure.getMessage(),
            instance(request),
            null);
    return ResponseEntity.status(404).body(body);
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
  ResponseEntity<ProblemDetail> invalid(Exception failure, HttpServletRequest request) {
    ErrorCode code = ErrorCode.VALIDATION_FAILED;
    return ResponseEntity.badRequest()
        .body(code.toProblemDetail("Request validation failed", instance(request), null));
  }

  private static URI instance(HttpServletRequest request) {
    return URI.create(request.getRequestURI());
  }
}
