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

  public CommitResult commit(UUID betId, String reservationToken) {
    if (reservationToken == null || !reservationToken.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("reservationToken must be lowercase SHA-256");
    }
    try {
      http.put()
          .uri(RESERVATIONS + "/{betId}/commit", betId)
          .header("X-Risk-Reservation-Token", reservationToken)
          .retrieve()
          .onStatus(
              status -> status.value() == HttpStatus.NOT_FOUND.value(),
              (request, ignored) -> {
                throw new ReservationMissing();
              })
          .onStatus(
              status -> status.value() == HttpStatus.CONFLICT.value(),
              (request, ignored) -> {
                throw new ReservationConflict();
              })
          .onStatus(
              status -> status.value() != HttpStatus.NO_CONTENT.value(),
              (request, ignored) -> {
                throw new DependencyUnavailableException("Risk commit was not accepted");
              })
          .toBodilessEntity();
      return CommitResult.COMMITTED;
    } catch (ReservationMissing exception) {
      return CommitResult.NOT_FOUND;
    } catch (ReservationConflict exception) {
      return CommitResult.CONFLICT;
    } catch (BetPlacementException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new DependencyUnavailableException("Risk commit is unavailable", exception);
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

  public enum CommitResult {
    COMMITTED,
    NOT_FOUND,
    CONFLICT
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

  private static final class ReservationMissing extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  private static final class ReservationConflict extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }
}
