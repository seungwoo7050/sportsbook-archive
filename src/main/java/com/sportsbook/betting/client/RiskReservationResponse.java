package com.sportsbook.betting.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.betting.error.DependencyUnavailableException;
import com.sportsbook.betting.error.ValidationFailedException;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiskReservationResponse(
    Boolean approved,
    boolean replayed,
    String rejectionReason,
    String reservationState,
    Instant expiresAt,
    String reservationToken) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record RiskProblem(String errorCode) {

  private static final ObjectMapper JSON = new ObjectMapper();

  static RuntimeException mapReservation(ClientHttpResponse response) {
    try {
      if (response.getStatusCode().value() != HttpStatus.BAD_REQUEST.value()) {
        return new DependencyUnavailableException("Risk reservation was not accepted");
      }
      RiskProblem problem = JSON.readValue(response.getBody(), RiskProblem.class);
      if ("VALIDATION_FAILED".equals(problem.errorCode())) {
        return new ValidationFailedException("Risk rejected an invalid reservation");
      }
      return new DependencyUnavailableException("Risk returned an unexpected validation problem");
    } catch (IOException unreadable) {
      return new DependencyUnavailableException("Risk returned an unreadable problem", unreadable);
    }
  }
}
