package com.sportsbook.betting.domain;

import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
  @Column(name = "slip_type", nullable = false, updatable = false, length = 16)
  private SlipKind slipKind;

  @Column(name = "system_min_wins")
  private Integer systemMinWins;

  @Column(name = "system_total_selections")
  private Integer systemTotalSelections;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "amount", column = @Column(name = "stake_amount", nullable = false)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "stake_currency", nullable = false, length = 3))
  })
  private EmbeddedMoney stake;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount", column = @Column(name = "max_payout_amount", nullable = false)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "max_payout_currency", nullable = false, length = 3))
  })
  private EmbeddedMoney maxPayout;

  @Column(name = "request_fingerprint", updatable = false, length = 64)
  private String requestFingerprint;

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

  @OneToMany(mappedBy = "bet", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("legIndex ASC")
  private List<BetLeg> legs = new ArrayList<>();

  protected Bet() {}

  private Bet(BetDraft draft) {
    this.betId = draft.betId();
    this.userId = draft.userId();
    this.betReference = draft.reference();
    this.slipKind = SlipKind.of(draft.slipType());
    if (draft.slipType() instanceof BetSlipType.System system) {
      this.systemMinWins = system.minWins();
      this.systemTotalSelections = system.totalSelections();
    }
    this.stake = EmbeddedMoney.of(draft.stake());
    this.maxPayout = EmbeddedMoney.of(draft.maxPayout());
    this.requestFingerprint = draft.requestFingerprint();
    this.idempotencyKey = draft.idempotencyKey().value();
    this.status = BetStatus.PENDING;
    this.createdAt = draft.createdAt();
    this.updatedAt = draft.createdAt();
  }

  static Bet from(BetDraft draft) {
    return new Bet(draft);
  }

  public static Bet pending(BetDraft draft, List<BetLeg> legs) {
    Objects.requireNonNull(legs, "legs");
    requireStructure(draft.slipType(), legs.size());
    Bet bet = new Bet(draft);
    for (int index = 0; index < legs.size(); index++) {
      BetLeg leg = Objects.requireNonNull(legs.get(index), "leg");
      leg.assignTo(bet, index);
      bet.legs.add(leg);
    }
    return bet;
  }

  private static void requireStructure(BetSlipType type, int legCount) {
    if (type instanceof BetSlipType.Single && legCount != 1) {
      throw new IllegalArgumentException("SINGLE requires exactly one leg");
    }
    if (type instanceof BetSlipType.Multiple && legCount < 2) {
      throw new IllegalArgumentException("MULTIPLE requires at least two legs");
    }
    if (type instanceof BetSlipType.System system && system.totalSelections() != legCount) {
      throw new IllegalArgumentException("SYSTEM totalSelections must equal leg count");
    }
  }

  private void requireSelectionEvent(UUID eventId) {
    if (legs.stream().noneMatch(leg -> leg.eventId().equals(eventId))) {
      throw new IllegalArgumentException("Resolution event must belong to a selected leg");
    }
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

  public BetSlipType slipType() {
    return switch (slipKind) {
      case SINGLE -> new BetSlipType.Single();
      case MULTIPLE -> new BetSlipType.Multiple();
      case SYSTEM -> new BetSlipType.System(systemMinWins, systemTotalSelections);
    };
  }

  public Money stake() {
    return stake.toMoney();
  }

  public Money maxPayout() {
    return maxPayout.toMoney();
  }

  public String requestFingerprint() {
    return requestFingerprint;
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

  public List<BetLeg> legs() {
    return List.copyOf(legs);
  }
}
