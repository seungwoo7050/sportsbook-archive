package com.sportsbook.settlement.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.sportsbook.settlement.admin")
public final class AdminExceptionHandler {

  @ExceptionHandler(AdminControlException.class)
  ResponseEntity<ProblemDetail> control(AdminControlException failure, HttpServletRequest request) {
    return response(failure.status(), failure.getMessage(), request);
  }

  @ExceptionHandler({
    MissingRequestHeaderException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception ignored, HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, "The admin request is invalid", request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception ignored, HttpServletRequest request) {
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "The admin request failed", request);
  }

  private static ResponseEntity<ProblemDetail> response(
      HttpStatus status, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}
