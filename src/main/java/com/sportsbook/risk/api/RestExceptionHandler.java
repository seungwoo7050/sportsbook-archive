package com.sportsbook.risk.api;

import com.sportsbook.protocol.error.ErrorCode;
import com.sportsbook.protocol.error.ProblemDetail;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Renders every controller failure with the shared protocol problem shape. */
@RestControllerAdvice
public class RestExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException exception) {
    String detail =
        exception.getBindingResult().getAllErrors().stream()
            .map(error -> error.getDefaultMessage())
            .filter(message -> message != null && !message.isBlank())
            .findFirst()
            .orElse("Request validation failed");
    return problem(ErrorCode.VALIDATION_FAILED, detail);
  }

  @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
  ResponseEntity<ProblemDetail> invalidMethod(Exception exception) {
    return problem(ErrorCode.VALIDATION_FAILED, "Request validation failed");
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingRequestHeaderException.class
  })
  ResponseEntity<ProblemDetail> malformed(Exception exception) {
    return problem(ErrorCode.VALIDATION_FAILED, "Request payload, path, or headers are malformed");
  }

  @ExceptionHandler(RiskApiException.class)
  ResponseEntity<ProblemDetail> reservation(RiskApiException exception) {
    return ResponseEntity.status(exception.status())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(exception.problem());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> internal(Exception exception) {
    log.error("Unhandled internal request failure", exception);
    return problem(ErrorCode.INTERNAL_ERROR, "The request could not be completed");
  }

  private static ResponseEntity<ProblemDetail> problem(ErrorCode code, String detail) {
    return ResponseEntity.status(code.httpStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(code.toProblemDetail(detail));
  }
}
