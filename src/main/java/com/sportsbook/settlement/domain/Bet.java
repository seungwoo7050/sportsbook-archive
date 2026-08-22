package com.sportsbook.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** Aggregate root introduced first as the owner of selection identity. */
@Entity
@Table(name = "bet")
public class Bet {

  @Id
  @Column(name = "bet_id", nullable = false, updatable = false)
  private UUID betId;

  protected Bet() {}

  Bet(UUID betId) {
    this.betId = Objects.requireNonNull(betId, "betId");
  }

  public UUID betId() {
    return betId;
  }
}
