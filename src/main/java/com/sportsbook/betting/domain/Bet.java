package com.sportsbook.betting.domain;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.BetStatus;
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
        name = "amount",
        column = @Column(name = "max_payout_amount", nullable = false)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "max_payout_currency", nullable = false, length = 3))
  })
  private EmbeddedMoney maxPayout;

  @Column(name = "request_fingerprint", updatable = false, length = 64)
  private String requestFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "placement_phase", nullable = false, length = 32)
  private PlacementPhase placementPhase;

  @Column(name = "risk_reservation_expires_at")
  private Instant riskReservationExpiresAt;

  @Column(name = "risk_reservation_token", length = 64)
  private String riskReservationToken;

  @Column(name = "risk_commit_observed", nullable = false)
  private boolean riskCommitObserved;

  @Column(name = "wallet_operation_id")
  private UUID walletOperationId;

  @Column(name = "rejection_reason", length = 64)
  private String rejectionReason;

  @Column(name = "rejection_detail", length = 1024)
  private String rejectionDetail;

  @Enumerated(EnumType.STRING)
  @Column(name = "compensation_action", length = 24)
  private CompensationAction compensationAction;

  @Enumerated(EnumType.STRING)
  @Column(name = "compensation_state", nullable = false, length = 16)
  private CompensationState compensationState;

  @Column(name = "compensation_operation_id")
  private UUID compensationOperationId;

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
    this.placementPhase = PlacementPhase.CREATED;
    this.compensationState = CompensationState.NONE;
    this.createdAt = draft.createdAt();
    this.updatedAt = draft.createdAt();
  }

  public void recordRiskReservation(
      Instant expiresAt, String token, boolean alreadyCommitted, Instant now) {
    requireStatus(BetStatus.PENDING);
    requireNoCompensation();
    if (placementPhase != PlacementPhase.CREATED
        && placementPhase != PlacementPhase.RISK_RESERVED) {
      throw new IllegalStateException("Risk reservation cannot follow " + placementPhase);
    }
    if (token == null || !token.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("risk reservation token must be lowercase SHA-256");
    }
    this.placementPhase = PlacementPhase.RISK_RESERVED;
    this.riskReservationExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    this.riskReservationToken = token;
    this.riskCommitObserved = alreadyCommitted;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void confirmWallet(UUID operationId, Instant now) {
    requireStatus(BetStatus.PENDING);
    requireNoCompensation();
    if (placementPhase != PlacementPhase.RISK_RESERVED
        && placementPhase != PlacementPhase.WALLET_CONFIRMED) {
      throw new IllegalStateException("Wallet confirmation cannot follow " + placementPhase);
    }
    Objects.requireNonNull(operationId, "operationId");
    if (walletOperationId != null && !walletOperationId.equals(operationId)) {
      throw new IllegalStateException("Wallet returned conflicting operation ids");
    }
    this.walletOperationId = operationId;
    this.placementPhase = PlacementPhase.WALLET_CONFIRMED;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void commitRisk(Instant now) {
    requireStatus(BetStatus.PENDING);
    requireNoCompensation();
    if (placementPhase != PlacementPhase.WALLET_CONFIRMED) {
      throw new IllegalStateException("Risk commit cannot follow " + placementPhase);
    }
    this.riskCommitObserved = true;
    this.placementPhase = PlacementPhase.RISK_COMMITTED;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void accept(Instant now) {
    requireStatus(BetStatus.PENDING);
    requireNoCompensation();
    if (placementPhase != PlacementPhase.RISK_COMMITTED || !riskCommitObserved) {
      throw new IllegalStateException("Acceptance requires committed risk proof");
    }
    this.status = BetStatus.ACCEPTED;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void rejectAtCreation(String reason, String detail, Instant now) {
    requireStatus(BetStatus.PENDING);
    if (placementPhase != PlacementPhase.CREATED) {
      throw new IllegalStateException("Creation rejection cannot follow " + placementPhase);
    }
    this.status = BetStatus.REJECTED;
    this.rejectionReason = requireText(reason, "reason");
    this.rejectionDetail = requireText(detail, "detail");
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void requireRiskRelease(String reason, String detail, Instant now) {
    requireCompensation(
        PlacementPhase.RISK_RESERVED, CompensationAction.RISK_RELEASE, reason, detail, now);
  }

  public void requireWalletRefund(String reason, String detail, Instant now) {
    requireCompensation(
        PlacementPhase.WALLET_CONFIRMED, CompensationAction.WALLET_REFUND, reason, detail, now);
  }

  private void requireCompensation(
      PlacementPhase expected,
      CompensationAction action,
      String reason,
      String detail,
      Instant now) {
    requireStatus(BetStatus.PENDING);
    requireNoCompensation();
    if (placementPhase != expected) {
      throw new IllegalStateException(action + " cannot follow " + placementPhase);
    }
    this.compensationAction = action;
    this.compensationState = CompensationState.REQUIRED;
    this.rejectionReason = requireText(reason, "reason");
    this.rejectionDetail = requireText(detail, "detail");
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  private void requireNoCompensation() {
    if (compensationState != CompensationState.NONE || compensationAction != null) {
      throw new IllegalStateException("Forward placement is fenced by compensation");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  private void requireStatus(BetStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("Expected " + expected + " but was " + status);
    }
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

  public PlacementPhase placementPhase() {
    return placementPhase;
  }

  public Instant riskReservationExpiresAt() {
    return riskReservationExpiresAt;
  }

  public String riskReservationToken() {
    return riskReservationToken;
  }

  public boolean riskCommitObserved() {
    return riskCommitObserved;
  }

  public UUID walletOperationId() {
    return walletOperationId;
  }

  public String rejectionReason() {
    return rejectionReason;
  }

  public String rejectionDetail() {
    return rejectionDetail;
  }

  public CompensationAction compensationAction() {
    return compensationAction;
  }

  public CompensationState compensationState() {
    return compensationState;
  }

  public UUID compensationOperationId() {
    return compensationOperationId;
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
