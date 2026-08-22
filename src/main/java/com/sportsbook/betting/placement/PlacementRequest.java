package com.sportsbook.betting.placement;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.protocol.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "placement_request")
public class PlacementRequest {

  @Id
  @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "request_fingerprint", updatable = false, length = 64)
  private String requestFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, updatable = false, length = 16)
  private PlacementOutcome outcome;

  @Column(name = "bet_id", updatable = false)
  private UUID betId;

  @Column(name = "error_code", updatable = false, length = 64)
  private String errorCode;

  @Column(name = "error_detail", updatable = false, length = 1024)
  private String errorDetail;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PlacementRequest() {}

  private PlacementRequest(
      String key,
      UUID userId,
      String fingerprint,
      PlacementOutcome outcome,
      UUID betId,
      String errorCode,
      String errorDetail,
      Instant createdAt) {
    this.idempotencyKey = Objects.requireNonNull(key, "key");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.requestFingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    this.outcome = Objects.requireNonNull(outcome, "outcome");
    this.betId = betId;
    this.errorCode = errorCode;
    this.errorDetail = errorDetail;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static PlacementRequest forBet(Bet bet) {
    return new PlacementRequest(
        bet.idempotencyKey(),
        bet.userId(),
        bet.requestFingerprint(),
        PlacementOutcome.BET,
        bet.betId(),
        null,
        null,
        bet.createdAt());
  }

  public static PlacementRequest rejected(
      String key, UUID userId, String fingerprint, ErrorCode code, String detail, Instant at) {
    return new PlacementRequest(
        key, userId, fingerprint, PlacementOutcome.REJECTION, null, code.name(), detail, at);
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public UUID userId() {
    return userId;
  }

  public String requestFingerprint() {
    return requestFingerprint;
  }

  public PlacementOutcome outcome() {
    return outcome;
  }

  public UUID betId() {
    return betId;
  }

  public String errorCode() {
    return errorCode;
  }

  public String errorDetail() {
    return errorDetail;
  }
}
