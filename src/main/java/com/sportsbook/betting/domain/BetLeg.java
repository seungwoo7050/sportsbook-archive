package com.sportsbook.betting.domain;

import com.sportsbook.betting.infrastructure.id.UuidV7;
import com.sportsbook.protocol.value.Odds;
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

@Entity
@Table(name = "bet_leg")
public class BetLeg {

  @Id
  @Column(name = "leg_id", nullable = false, updatable = false)
  private UUID legId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "bet_id", nullable = false)
  private Bet bet;

  @Column(name = "leg_index", nullable = false)
  private int legIndex;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "market_id", nullable = false)
  private UUID marketId;

  @Column(name = "selection_id", nullable = false)
  private UUID selectionId;

  @Column(name = "odds_at_submission", nullable = false, precision = 9, scale = 4)
  private BigDecimal oddsAtSubmission;

  protected BetLeg() {}

  private BetLeg(UUID eventId, UUID marketId, UUID selectionId, Odds odds) {
    this.legId = UuidV7.generate();
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.marketId = Objects.requireNonNull(marketId, "marketId");
    this.selectionId = Objects.requireNonNull(selectionId, "selectionId");
    this.oddsAtSubmission = Objects.requireNonNull(odds, "odds").decimal();
  }

  public static BetLeg create(UUID eventId, UUID marketId, UUID selectionId, Odds odds) {
    return new BetLeg(eventId, marketId, selectionId, odds);
  }

  void assignTo(Bet owner, int index) {
    this.bet = Objects.requireNonNull(owner, "owner");
    this.legIndex = index;
  }

  public UUID legId() {
    return legId;
  }

  public int legIndex() {
    return legIndex;
  }

  public UUID eventId() {
    return eventId;
  }

  public UUID marketId() {
    return marketId;
  }

  public UUID selectionId() {
    return selectionId;
  }

  public Odds oddsAtSubmission() {
    return Odds.ofDecimal(oddsAtSubmission);
  }
}
