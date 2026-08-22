package com.sportsbook.betting.domain;

import com.sportsbook.protocol.domain.BetStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bet")
public class Bet {

  @Id
  @Column(name = "bet_id", nullable = false, updatable = false)
  private UUID betId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "bet_reference", nullable = false, updatable = false, length = 32)
  private String betReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private BetStatus status;

  @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
  private String idempotencyKey;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Bet() {}

  private Bet(BetDraft draft) {
    this.betId = draft.betId();
    this.userId = draft.userId();
    this.betReference = draft.reference();
    this.idempotencyKey = draft.idempotencyKey().value();
    this.status = BetStatus.PENDING;
    this.createdAt = draft.createdAt();
    this.updatedAt = draft.createdAt();
  }

  static Bet from(BetDraft draft) {
    return new Bet(draft);
  }

  public UUID betId() {
    return betId;
  }

  public UUID userId() {
    return userId;
  }

  public String betReference() {
    return betReference;
  }

  public BetStatus status() {
    return status;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
