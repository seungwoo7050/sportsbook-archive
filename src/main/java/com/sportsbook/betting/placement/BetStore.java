package com.sportsbook.betting.placement;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.CompensationAction;
import com.sportsbook.betting.domain.CompensationState;
import com.sportsbook.betting.domain.PlacementPhase;
import com.sportsbook.betting.outbox.OutboxEvent;
import com.sportsbook.betting.outbox.OutboxEventRepository;
import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.betting.persistence.PlacementRequestRepository;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.error.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BetStore {

  private final BetRepository bets;
  private final OutboxEventRepository outbox;
  private final PlacementRequestRepository requests;

  public BetStore(
      BetRepository bets, OutboxEventRepository outbox, PlacementRequestRepository requests) {
    this.bets = bets;
    this.outbox = outbox;
    this.requests = requests;
  }

  @Transactional(readOnly = true)
  public Optional<PlacementRequest> findPlacementRequest(String idempotencyKey) {
    return requests.findById(idempotencyKey);
  }

  @Transactional(readOnly = true)
  public Optional<Bet> findByIdempotencyKey(String idempotencyKey) {
    return bets.findByIdempotencyKey(idempotencyKey);
  }

  @Transactional(readOnly = true)
  public Bet findById(UUID betId) {
    return bets.findWithLegsByBetId(betId)
        .orElseThrow(() -> new IllegalStateException("Bet not found during placement: " + betId));
  }

  @Transactional
  public void savePending(Bet bet) {
    bets.saveAndFlush(bet);
    requests.saveAndFlush(PlacementRequest.forBet(bet));
  }

  @Transactional
  public void savePreflightRejection(
      String key, UUID userId, String fingerprint, ErrorCode code, String detail, Instant now) {
    requests.saveAndFlush(PlacementRequest.rejected(key, userId, fingerprint, code, detail, now));
  }

  @Transactional
  public void recordRiskReservation(
      UUID betId, Instant expiresAt, String token, boolean alreadyCommitted, Instant now) {
    pending(betId).recordRiskReservation(expiresAt, token, alreadyCommitted, now);
  }

  @Transactional
  public void confirmWallet(UUID betId, UUID operationId, Instant now) {
    pending(betId).confirmWallet(operationId, now);
  }

  @Transactional
  public void commitRisk(UUID betId, Instant now) {
    Bet bet = locked(betId);
    if (bet.status() == BetStatus.ACCEPTED
        || (bet.status() == BetStatus.PENDING
            && bet.placementPhase() == PlacementPhase.RISK_COMMITTED)) {
      return;
    }
    requirePending(bet).commitRisk(now);
  }

  @Transactional
  public void requireRiskRelease(UUID betId, ErrorCode reason, String detail, Instant now) {
    Bet bet = locked(betId);
    if (bet.status() == BetStatus.REJECTED
        || bet.compensationAction() == CompensationAction.RISK_RELEASE) {
      return;
    }
    requirePending(bet).requireRiskRelease(reason.name(), detail, now);
  }

  @Transactional
  public void requireWalletRefund(UUID betId, ErrorCode reason, String detail, Instant now) {
    Bet bet = locked(betId);
    if (bet.status() == BetStatus.REJECTED
        || bet.compensationAction() == CompensationAction.WALLET_REFUND) {
      return;
    }
    requirePending(bet).requireWalletRefund(reason.name(), detail, now);
  }

  @Transactional
  public void beginCompensation(UUID betId, Instant now) {
    Bet bet = locked(betId);
    if (bet.status() == BetStatus.REJECTED
        || bet.compensationState() == CompensationState.IN_PROGRESS
        || bet.compensationState() == CompensationState.COMPLETED) {
      return;
    }
    requirePending(bet).beginCompensation(now);
  }

  @Transactional
  public void completeRiskRelease(UUID betId, boolean committedConflict, Instant now) {
    Bet bet = locked(betId);
    if (bet.status() == BetStatus.REJECTED
        || (bet.compensationAction() == CompensationAction.RISK_RELEASE
            && bet.compensationState() == CompensationState.COMPLETED)) {
      return;
    }
    requirePending(bet).completeRiskRelease(committedConflict, now);
  }

  @Transactional
  public void completeWalletRefund(UUID betId, UUID operationId, Instant now) {
    Bet bet = locked(betId);
    if (bet.status() == BetStatus.REJECTED) {
      return;
    }
    if (bet.compensationState() == CompensationState.COMPLETED) {
      if (!operationId.equals(bet.compensationOperationId())) {
        throw new IllegalStateException("Wallet returned conflicting refund operation ids");
      }
      return;
    }
    requirePending(bet).completeWalletRefund(operationId, now);
  }

  @Transactional
  public Bet rejectAtCreation(UUID betId, ErrorCode reason, String detail, Instant now) {
    Bet bet = locked(betId);
    if (bet.status() == BetStatus.PENDING) {
      bet.rejectAtCreation(reason.name(), detail, now);
    }
    return bet;
  }

  @Transactional
  public Bet rejectAfterCompensation(UUID betId, Instant now) {
    Bet bet = locked(betId);
    if (bet.status() == BetStatus.PENDING) {
      bet.rejectAfterCompensation(now);
    }
    return bet;
  }

  @Transactional
  public Bet acceptAndEnqueue(UUID betId, OutboxEvent event, Instant now) {
    Bet bet = locked(betId);
    if (bet.status() == BetStatus.ACCEPTED) {
      return bet;
    }
    if (bet.status() != BetStatus.PENDING) {
      throw new IllegalStateException("Cannot accept terminal bet " + betId);
    }
    bet.accept(now);
    outbox.save(event);
    return bet;
  }

  private Bet pending(UUID betId) {
    return requirePending(locked(betId));
  }

  private Bet requirePending(Bet bet) {
    if (bet.status() != BetStatus.PENDING) {
      throw new IllegalStateException("Placement cannot update terminal bet " + bet.betId());
    }
    return bet;
  }

  private Bet locked(UUID betId) {
    return bets.findLockedByBetId(betId)
        .orElseThrow(() -> new IllegalStateException("Bet vanished during placement: " + betId));
  }
}
