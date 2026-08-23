package com.sportsbook.admin.api;

import com.sportsbook.admin.audit.AuditPersistenceException;
import com.sportsbook.admin.client.DownstreamContractException;
import com.sportsbook.admin.client.DownstreamStatusException;
import com.sportsbook.admin.client.DownstreamUnavailableException;
import com.sportsbook.admin.context.AdminContextArgumentResolver;
import com.sportsbook.protocol.error.ErrorCode;
import com.sportsbook.protocol.error.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public final class AdminExceptionHandler {

  private static final URI AUDIT_UNAVAILABLE =
      URI.create("https://sportsbook/errors/audit-unavailable");
  private static final URI BAD_GATEWAY = URI.create("https://sportsbook/errors/bad-gateway");
  private static final URI GATEWAY_TIMEOUT =
      URI.create("https://sportsbook/errors/gateway-timeout");
  private static final URI CONTRACT_VIOLATION =
      URI.create("https://sportsbook/errors/downstream-contract-violation");

  @ExceptionHandler(DownstreamStatusException.class)
  ResponseEntity<byte[]> relayDownstream(DownstreamStatusException failure) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(
        failure.contentType() == null ? MediaType.APPLICATION_PROBLEM_JSON : failure.contentType());
    headers.setCacheControl("no-store");
    return new ResponseEntity<>(failure.body(), headers, failure.status());
  }

  @ExceptionHandler(DownstreamUnavailableException.class)
  ResponseEntity<ProblemDetail> downstreamUnavailable(
      DownstreamUnavailableException failure, HttpServletRequest request) {
    if (failure.reason() == DownstreamUnavailableException.Reason.TIMEOUT) {
      return problem(
          HttpStatus.GATEWAY_TIMEOUT,
          GATEWAY_TIMEOUT,
          "GATEWAY_TIMEOUT",
          "The downstream outcome is unknown after a timeout",
          request);
    }
    return problem(
        HttpStatus.BAD_GATEWAY,
        BAD_GATEWAY,
        "BAD_GATEWAY",
        "The downstream outcome is unknown",
        request);
  }

  @ExceptionHandler(DownstreamContractException.class)
  ResponseEntity<ProblemDetail> downstreamContract(
      DownstreamContractException failure, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_GATEWAY,
        CONTRACT_VIOLATION,
        "DOWNSTREAM_CONTRACT_VIOLATION",
        "The downstream success response violated its contract",
        request);
  }

  @ExceptionHandler({
    AdminRequestException.class,
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ServletRequestBindingException.class
  })
  ResponseEntity<ProblemDetail> invalidRequest(Exception failure, HttpServletRequest request) {
    ProblemDetail body =
        ErrorCode.VALIDATION_FAILED.toProblemDetail(
            "The admin request is invalid",
            URI.create(request.getRequestURI()),
            MDC.get("traceId"));
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(body);
  }

  @ExceptionHandler(AuditPersistenceException.class)
  ResponseEntity<ProblemDetail> auditPersistence(
      AuditPersistenceException failure, HttpServletRequest request) {
    String code =
        failure.phase() == AuditPersistenceException.Phase.BEGIN
            ? "AUDIT_UNAVAILABLE"
            : "AUDIT_FINALIZATION_FAILED";
    String detail =
        failure.phase() == AuditPersistenceException.Phase.BEGIN
            ? "The audit trail could not be started"
            : "The audit trail could not be finalized";
    ProblemDetail body =
        new ProblemDetail(
            AUDIT_UNAVAILABLE,
            "Service Unavailable",
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            code,
            detail,
            URI.create(request.getRequestURI()),
            MDC.get("traceId"));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    headers.setCacheControl("no-store");
    headers.set(AdminContextArgumentResolver.ACTION_ID_HEADER, failure.actionId().toString());
    return new ResponseEntity<>(body, headers, HttpStatus.SERVICE_UNAVAILABLE);
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, URI type, String code, String detail, HttpServletRequest request) {
    ProblemDetail body =
        new ProblemDetail(
            type,
            status.getReasonPhrase(),
            status.value(),
            code,
            detail,
            URI.create(request.getRequestURI()),
            MDC.get("traceId"));
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(body);
  }
}
