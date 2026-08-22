package com.sportsbook.betting.domain;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.domain.SettlementResult;
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
  @Column(name = "settlement_result", length = 8)
  private SettlementResult settlementResult;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "amount", column = @Column(name = "settled_payout_amount")),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "settled_payout_currency", length = 3))
  })
  private EmbeddedMoney settledPayout;

  @Enumerated(EnumType.STRING)
  @Column(name = "void_reason", length = 24)
  private VoidReason voidReason;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolution_event_id")
  private UUID resolutionEventId;

  @Column(name = "resolution_revision_id")
  private UUID resolutionRevisionId;

  @Column(name = "resolution_revision_number")
  private Long resolutionRevisionNumber;

  @Column(name = "resolution_payload_sha256", length = 64)
  private String resolutionPayloadSha256;

  @Column(name = "source_result_settled_at")
  private Instant sourceResultSettledAt;

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

  @Column(name = "reconciliation_requested_at")
  private Instant reconciliationRequestedAt;

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

  public void beginCompensation(Instant now) {
    requireStatus(BetStatus.PENDING);
    if (compensationAction == null || compensationState != CompensationState.REQUIRED) {
      throw new IllegalStateException("Compensation intent is required");
    }
    this.compensationState = CompensationState.IN_PROGRESS;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void completeRiskRelease(boolean committedConflict, Instant now) {
    requireCompensationInProgress(CompensationAction.RISK_RELEASE);
    this.riskCommitObserved = this.riskCommitObserved || committedConflict;
    this.compensationState = CompensationState.COMPLETED;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void completeWalletRefund(UUID operationId, Instant now) {
    requireCompensationInProgress(CompensationAction.WALLET_REFUND);
    Objects.requireNonNull(operationId, "operationId");
    if (compensationOperationId != null && !compensationOperationId.equals(operationId)) {
      throw new IllegalStateException("Wallet returned conflicting refund operation ids");
    }
    this.compensationOperationId = operationId;
    this.compensationState = CompensationState.COMPLETED;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void rejectAfterCompensation(Instant now) {
    requireStatus(BetStatus.PENDING);
    if (compensationState != CompensationState.COMPLETED || rejectionReason == null) {
      throw new IllegalStateException("Completed compensation is required");
    }
    this.status = BetStatus.REJECTED;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void settleBase(
      UUID eventId,
      SettlementResult result,
      Money eventStake,
      Money payout,
      Instant settledAt,
      String payloadHash) {
    requireStatus(BetStatus.ACCEPTED);
    requireSelectionEvent(eventId);
    if (!stake.toMoney().equals(eventStake)) {
      throw new IllegalArgumentException("Settlement stake does not match original unit stake");
    }
    if (payout.currency() != stake.currency() || payout.isNegative()) {
      throw new IllegalArgumentException("Settlement payout is invalid");
    }
    this.status = BetStatus.SETTLED;
    this.settlementResult = Objects.requireNonNull(result, "result");
    this.settledPayout = EmbeddedMoney.of(payout);
    recordBaseResolution(eventId, settledAt, payloadHash);
  }

  public void voidBase(UUID eventId, VoidReason reason, Instant voidedAt, String payloadHash) {
    requireStatus(BetStatus.ACCEPTED);
    requireSelectionEvent(eventId);
    this.status = BetStatus.VOIDED;
    this.voidReason = Objects.requireNonNull(reason, "reason");
    recordBaseResolution(eventId, voidedAt, payloadHash);
  }

  private void recordBaseResolution(UUID eventId, Instant at, String payloadHash) {
    this.resolutionEventId = Objects.requireNonNull(eventId, "eventId");
    this.resolutionRevisionNumber = 0L;
    this.resolutionPayloadSha256 = requireHash(payloadHash);
    this.sourceResultSettledAt = Objects.requireNonNull(at, "at");
    this.resolvedAt = at;
    this.updatedAt = at;
  }

  private static String requireHash(String value) {
    if (value == null || !value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("payload hash must be lowercase SHA-256");
    }
    return value;
  }

  public RevisionApplyResult applyRevision(
      UUID eventId,
      UUID revisionId,
      long revisionNumber,
      SettlementResult previousResult,
      SettlementResult newResult,
      Money previousPayout,
      Money newPayout,
      Instant sourceSettledAt,
      Instant revisedAt,
      String payloadHash) {
    if (status != BetStatus.ACCEPTED && status != BetStatus.SETTLED) {
      throw new IllegalStateException("Revisions require ACCEPTED or SETTLED status");
    }
    if (revisionNumber < 1) {
      throw new IllegalArgumentException("revisionNumber must be at least 1");
    }
    long current = resolutionRevisionNumber == null ? 0 : resolutionRevisionNumber;
    if (revisionNumber < current) {
      return RevisionApplyResult.IGNORED;
    }
    if (revisionNumber == current) {
      if (Objects.equals(resolutionRevisionId, revisionId)
          && Objects.equals(resolutionPayloadSha256, payloadHash)) {
        return RevisionApplyResult.DUPLICATE;
      }
      throw new IllegalStateException("Conflicting equal resolution revision");
    }
    boolean gap = revisionNumber > current + 1;
    if (!gap && status == BetStatus.SETTLED) {
      if (settlementResult != previousResult || !Objects.equals(settledPayout(), previousPayout)) {
        throw new IllegalStateException("Revision previous snapshot does not match projection");
      }
    }
    if (newPayout.currency() != stake.currency() || newPayout.isNegative()) {
      throw new IllegalArgumentException("Revision payout is invalid");
    }
    this.status = BetStatus.SETTLED;
    this.settlementResult = Objects.requireNonNull(newResult, "newResult");
    this.settledPayout = EmbeddedMoney.of(newPayout);
    this.voidReason = null;
    this.resolutionEventId = Objects.requireNonNull(eventId, "eventId");
    this.resolutionRevisionId = Objects.requireNonNull(revisionId, "revisionId");
    this.resolutionRevisionNumber = revisionNumber;
    this.resolutionPayloadSha256 = requireHash(payloadHash);
    this.sourceResultSettledAt = Objects.requireNonNull(sourceSettledAt, "sourceSettledAt");
    this.resolvedAt = Objects.requireNonNull(revisedAt, "revisedAt");
    this.updatedAt = revisedAt;
    return gap ? RevisionApplyResult.APPLIED_WITH_GAP : RevisionApplyResult.APPLIED;
  }

  public enum RevisionApplyResult {
    APPLIED,
    APPLIED_WITH_GAP,
    DUPLICATE,
    IGNORED
  }

  private void requireCompensationInProgress(CompensationAction action) {
    requireStatus(BetStatus.PENDING);
    if (compensationAction != action || compensationState != CompensationState.IN_PROGRESS) {
      throw new IllegalStateException(action + " must be in progress");
    }
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

  public SettlementResult settlementResult() {
    return settlementResult;
  }

  public Money settledPayout() {
    return settledPayout == null ? null : settledPayout.toMoney();
  }

  public VoidReason voidReason() {
    return voidReason;
  }

  public Instant resolvedAt() {
    return resolvedAt;
  }

  public long resolutionRevisionNumber() {
    return resolutionRevisionNumber == null ? -1 : resolutionRevisionNumber;
  }

  public UUID resolutionRevisionId() {
    return resolutionRevisionId;
  }

  public boolean hasResolution(UUID eventId, String payloadHash) {
    return Objects.equals(resolutionEventId, eventId)
        && Objects.equals(resolutionPayloadSha256, payloadHash);
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

  public void requestReconciliation(Instant at) {
    reconciliationRequestedAt = Objects.requireNonNull(at, "at");
  }

  public Instant reconciliationRequestedAt() {
    return reconciliationRequestedAt;
  }

  public List<BetLeg> legs() {
    return List.copyOf(legs);
  }
}
