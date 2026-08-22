package com.sportsbook.settlement.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pending read-model aggregate reconstructed without reading betting storage. */
@Entity
@Table(name = "bet")
public class Bet {

  @Id
  @Column(name = "bet_id", nullable = false, updatable = false)
  private UUID betId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "slip_type", nullable = false, updatable = false)
  private SlipKind slipKind;

  @Column(name = "system_min_wins", updatable = false)
  private Integer systemMinimumWins;

  @Column(name = "system_total_selections", updatable = false)
  private Integer systemTotalSelections;

  @Embedded private EmbeddedMoney stake;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SettlementStatus status;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "bet", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<BetSelection> selections = new ArrayList<>();

  protected Bet() {}

  private Bet(
      UUID betId,
      UUID userId,
      SlipKind slipKind,
      Integer minimumWins,
      Integer totalSelections,
      EmbeddedMoney stake,
      Instant requestedAt,
      Instant now) {
    this.betId = Objects.requireNonNull(betId, "betId");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.slipKind = Objects.requireNonNull(slipKind, "slipKind");
    this.systemMinimumWins = minimumWins;
    this.systemTotalSelections = totalSelections;
    this.stake = Objects.requireNonNull(stake, "stake");
    this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
    this.createdAt = Objects.requireNonNull(now, "now");
    this.updatedAt = now;
    this.status = SettlementStatus.PENDING;
  }

  public static Bet pending(
      UUID betId,
      UUID userId,
      SlipKind slipKind,
      Integer minimumWins,
      Integer totalSelections,
      EmbeddedMoney stake,
      Instant requestedAt,
      List<BetSelection> selections,
      Instant now) {
    Bet bet =
        new Bet(betId, userId, slipKind, minimumWins, totalSelections, stake, requestedAt, now);
    selections.forEach(
        selection -> {
          selection.attach(bet, bet.selections.size());
          bet.selections.add(selection);
        });
    bet.slipKind.toProtocol(minimumWins, totalSelections);
    return bet;
  }

  public UUID betId() {
    return betId;
  }

  public SettlementStatus status() {
    return status;
  }

  public com.sportsbook.protocol.domain.BetSlipType slipType() {
    return slipKind.toProtocol(systemMinimumWins, systemTotalSelections);
  }

  public List<BetSelection> selections() {
    return Collections.unmodifiableList(selections);
  }
}
