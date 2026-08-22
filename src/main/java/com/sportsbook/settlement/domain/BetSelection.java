package com.sportsbook.settlement.domain;

import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.infrastructure.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Immutable placement identity for one ordered bet leg. */
@Entity
@Table(name = "bet_selection")
public class BetSelection {

  @Id
  @Column(name = "selection_row_id", nullable = false, updatable = false)
  private UUID selectionRowId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "bet_id", nullable = false)
  private Bet bet;

  @Column(name = "leg_index", nullable = false, updatable = false)
  private int legIndex;

  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "market_id", nullable = false, updatable = false)
  private UUID marketId;

  @Column(name = "selection_id", nullable = false, updatable = false)
  private UUID selectionId;

  @Column(name = "odds", nullable = false, precision = 9, scale = 4, updatable = false)
  private BigDecimal odds;

  protected BetSelection() {}

  public BetSelection(UUID eventId, UUID marketId, UUID selectionId, Odds odds) {
    this.selectionRowId = UuidV7.generate();
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.marketId = Objects.requireNonNull(marketId, "marketId");
    this.selectionId = Objects.requireNonNull(selectionId, "selectionId");
    this.odds = Objects.requireNonNull(odds, "odds").decimal();
  }

  void attach(Bet owner, int index) {
    this.bet = Objects.requireNonNull(owner, "owner");
    this.legIndex = index;
  }

  public UUID selectionRowId() { return selectionRowId; }

  public int legIndex() { return legIndex; }

  public UUID eventId() { return eventId; }

  public UUID marketId() { return marketId; }

  public UUID selectionId() { return selectionId; }

  public Odds odds() { return Odds.ofDecimal(odds); }
}
