package com.sportsbook.betting.client;

import com.sportsbook.betting.error.BetPlacementException;
import com.sportsbook.betting.error.DependencyUnavailableException;
import com.sportsbook.betting.error.DuplicateBetException;
import com.sportsbook.betting.error.RiskLimitException;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RiskClient {

  private static final String RESERVATIONS = "/internal/v1/risk/reservations";

  private final RestClient http;

  public RiskClient(@Qualifier("riskRestClient") RestClient http) {
    this.http = http;
  }

  public Reservation reserve(UUID betId, UUID userId, Money fullExposure, List<UUID> selectionIds) {
    try {
      RiskReservationResponse response =
          http.post()
              .uri(RESERVATIONS)
              .contentType(MediaType.APPLICATION_JSON)
              .body(RiskReservationRequest.of(betId, userId, fullExposure, selectionIds))
              .retrieve()
              .onStatus(
                  status -> status.value() == HttpStatus.CONFLICT.value(),
                  (request, ignored) -> {
                    throw new DuplicateBetException("Risk reservation identity conflicts");
                  })
              .onStatus(
                  status -> status.value() != HttpStatus.OK.value(),
                  (request, error) -> {
                    throw RiskProblem.mapReservation(error);
                  })
              .body(RiskReservationResponse.class);
      return requireApproved(response);
    } catch (BetPlacementException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new DependencyUnavailableException("Risk reservation is unavailable", exception);
    }
  }

  private static Reservation requireApproved(RiskReservationResponse response) {
    if (response == null) {
      throw new DependencyUnavailableException("Risk returned an empty response");
    }
    if (!response.approved()) {
      throw new RiskLimitException(response.rejectionReason());
    }
    try {
      return new Reservation(
          ReservationState.valueOf(response.reservationState()),
          Objects.requireNonNull(response.expiresAt(), "expiresAt"),
          response.reservationToken());
    } catch (RuntimeException exception) {
      throw new DependencyUnavailableException("Risk returned an invalid reservation", exception);
    }
  }

  public enum ReservationState {
    RESERVED,
    COMMITTED
  }

  public record Reservation(ReservationState state, Instant expiresAt, String token) {
    public Reservation {
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(expiresAt, "expiresAt");
      if (token == null || !token.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("reservation token must be lowercase SHA-256");
      }
    }

    public boolean alreadyCommitted() {
      return state == ReservationState.COMMITTED;
    }
  }
}
