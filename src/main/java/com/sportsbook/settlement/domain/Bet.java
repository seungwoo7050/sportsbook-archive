package com.sportsbook.settlement.domain;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

  @Enumerated(EnumType.STRING)
  @Column(name = "result")
  private SettlementResult result;

  @Column(name = "payout_amount")
  private Long payoutAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "payout_currency")
  private Currency payoutCurrency;

  @Column(name = "settled_at")
  private Instant settledAt;

  @OneToMany(mappedBy = "bet", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("legIndex ASC")
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

  public UUID userId() {
    return userId;
  }

  public SettlementStatus status() {
    return status;
  }

  public com.sportsbook.protocol.domain.BetSlipType slipType() {
    return slipKind.toProtocol(systemMinimumWins, systemTotalSelections);
  }

  public Money stake() {
    return stake.toMoney();
  }

  public Instant requestedAt() {
    return requestedAt;
  }

  public SettlementResult result() {
    return result;
  }

  public Money payout() {
    return payoutAmount == null ? null : new Money(payoutAmount, payoutCurrency);
  }

  public Instant settledAt() {
    return settledAt;
  }

  public List<BetSelection> selections() {
    return Collections.unmodifiableList(selections);
  }

  public boolean applySelectionSnapshot(
      UUID eventId, Map<UUID, SettlementResult> outcomes, boolean clearMissing, Instant now) {
    if (status != SettlementStatus.PENDING) {
      throw new IllegalStateException("Cannot update selections after terminal settlement");
    }
    boolean changed = false;
    for (BetSelection selection : selections) {
      if (selection.eventId().equals(eventId)) {
        SettlementResult replacement = outcomes.get(selection.selectionId());
        if (replacement != null || clearMissing) {
          changed |= selection.replaceOutcome(replacement);
        }
      }
    }
    if (changed) {
      updatedAt = Objects.requireNonNull(now, "now");
    }
    return changed;
  }

  public boolean allSelectionsResolved() {
    return !selections.isEmpty() && selections.stream().allMatch(s -> s.outcome() != null);
  }

  public void recordSettled(SettlementResult outcome, Money payout, Instant now) {
    recordTerminal(SettlementStatus.SETTLED, outcome, payout, now);
  }

  public void recordVoided(Money refund, Instant now) {
    recordTerminal(SettlementStatus.VOIDED, SettlementResult.VOID, refund, now);
  }

  private void recordTerminal(
      SettlementStatus target, SettlementResult outcome, Money payout, Instant now) {
    if (status != SettlementStatus.PENDING) {
      throw new IllegalStateException("Bet is already terminal: " + status);
    }
    Objects.requireNonNull(payout, "payout");
    if (payout.amount() < 0 || payout.currency() != stake.toMoney().currency()) {
      throw new IllegalArgumentException("Payout must be nonnegative and use the stake currency");
    }
    status = target;
    result = Objects.requireNonNull(outcome, "outcome");
    payoutAmount = payout.amount();
    payoutCurrency = payout.currency();
    settledAt = Objects.requireNonNull(now, "now");
    updatedAt = now;
  }
}
