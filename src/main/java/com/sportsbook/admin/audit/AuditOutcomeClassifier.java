package com.sportsbook.admin.audit;

import com.sportsbook.admin.client.DownstreamContractException;
import com.sportsbook.admin.client.DownstreamStatusException;
import com.sportsbook.admin.client.DownstreamUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

final class AuditOutcomeClassifier {

  AuditDecision result(Object result) {
    int status = result instanceof ResponseEntity<?> response ? response.getStatusCode().value() : 200;
    if (HttpStatusCode.valueOf(status).is2xxSuccessful()) {
      return new AuditDecision(AuditOutcome.SUCCESS, status);
    }
    if (HttpStatusCode.valueOf(status).is4xxClientError()) {
      return new AuditDecision(AuditOutcome.FAILED, status);
    }
    return new AuditDecision(AuditOutcome.UNKNOWN, status);
  }

  AuditDecision failure(Throwable failure) {
    if (failure instanceof DownstreamStatusException rejection) {
      return new AuditDecision(AuditOutcome.FAILED, rejection.status().value());
    }
    if (failure instanceof DownstreamUnavailableException unavailable) {
      int status =
          unavailable.reason() == DownstreamUnavailableException.Reason.TIMEOUT
              ? HttpStatus.GATEWAY_TIMEOUT.value()
              : HttpStatus.BAD_GATEWAY.value();
      return new AuditDecision(AuditOutcome.UNKNOWN, status);
    }
    if (failure instanceof DownstreamContractException) {
      return new AuditDecision(AuditOutcome.UNKNOWN, HttpStatus.BAD_GATEWAY.value());
    }
    if (failure instanceof AccessDeniedException) {
      return new AuditDecision(AuditOutcome.FAILED, HttpStatus.FORBIDDEN.value());
    }
    if (failure instanceof IllegalArgumentException) {
      return new AuditDecision(AuditOutcome.FAILED, HttpStatus.BAD_REQUEST.value());
    }
    return new AuditDecision(AuditOutcome.UNKNOWN, HttpStatus.INTERNAL_SERVER_ERROR.value());
  }

  record AuditDecision(AuditOutcome outcome, Integer httpStatus) {
    AuditDecision {
      if (!outcome.isTerminal()) {
        throw new IllegalArgumentException("Audit decision must be terminal");
      }
    }
  }
}
