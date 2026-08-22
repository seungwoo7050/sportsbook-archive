package com.sportsbook.betting.placement;

import com.sportsbook.betting.client.RiskClient;
import com.sportsbook.betting.client.RiskClient.Reservation;
import com.sportsbook.betting.client.WalletClient;
import com.sportsbook.betting.client.WalletOperationResponse;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.domain.CompensationAction;
import com.sportsbook.betting.domain.CompensationState;
import com.sportsbook.betting.domain.SystemBetCalculator;
import com.sportsbook.betting.error.BetPlacementException;
import com.sportsbook.betting.error.DependencyUnavailableException;
import com.sportsbook.betting.error.DuplicateBetException;
import com.sportsbook.betting.error.InsufficientBalanceException;
import com.sportsbook.betting.error.MarketClosedException;
import com.sportsbook.betting.error.OddsDriftException;
import com.sportsbook.betting.error.RiskLimitException;
import com.sportsbook.betting.error.ValidationFailedException;
import com.sportsbook.betting.error.WalletRejectedException;
import com.sportsbook.betting.outbox.BetEventFactory;
import com.sportsbook.betting.outbox.OutboxEvent;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.error.ErrorCode;
import com.sportsbook.protocol.value.IdempotencyKey;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class BetPlacementService {

  private static final int MAX_ADVANCE_STEPS = 8;

  private final BetAssembler assembler;
  private final RiskClient risk;
  private final WalletClient wallet;
  private final SystemBetCalculator calculator;
  private final BetEventFactory events;
  private final IdempotencyCache idempotency;
  private final BetStore store;
  private final Clock clock;

  public BetPlacementService(
      BetAssembler assembler,
      RiskClient risk,
      WalletClient wallet,
      SystemBetCalculator calculator,
      BetEventFactory events,
      IdempotencyCache idempotency,
      BetStore store,
      Clock clock) {
    this.assembler = assembler;
    this.risk = risk;
    this.wallet = wallet;
    this.calculator = calculator;
    this.events = events;
    this.idempotency = idempotency;
    this.store = store;
    this.clock = clock;
  }

  public Bet place(PlaceBetCommand command) {
    String key = command.idempotencyKey().value();
    String fingerprint = RequestFingerprint.of(command);
    Optional<PlacementRequest> request = store.findPlacementRequest(key);
    if (request.isPresent()) {
      return PlacementReplay.request(request.get(), command.userId(), fingerprint, store::findById);
    }
    Optional<Bet> legacyBet = store.findByIdempotencyKey(key);
    if (legacyBet.isPresent()) {
      return PlacementReplay.bet(legacyBet.get(), command.userId(), fingerprint);
    }

    Bet bet;
    try {
      bet = assembler.assemble(command, fingerprint);
    } catch (BetPlacementException rejection) {
      if (!isDurablePreflight(rejection)) {
        throw rejection;
      }
      try {
        store.savePreflightRejection(
            key,
            command.userId(),
            fingerprint,
            rejection.errorCode(),
            rejection.getMessage(),
            clock.instant());
      } catch (DataIntegrityViolationException collision) {
        return replayKnown(key, command, fingerprint, collision);
      }
      throw rejection;
    }

    try {
      store.savePending(bet);
    } catch (DataIntegrityViolationException collision) {
      return replayKnown(key, command, fingerprint, collision);
    }
    return advance(bet.betId(), false, true);
  }

  private Bet advance(UUID betId, boolean recovery, boolean surfaceRejection) {
    for (int step = 0; step < MAX_ADVANCE_STEPS; step++) {
      Bet current = store.findById(betId);
      if (current.status() != BetStatus.PENDING) {
        return current;
      }
      try {
        if (current.compensationState() != CompensationState.NONE) {
          switch (current.compensationState()) {
            case REQUIRED -> store.beginCompensation(betId, clock.instant());
            case IN_PROGRESS -> performCompensation(current);
            case COMPLETED -> {
              return finishCompensatedRejection(current, surfaceRejection);
            }
            case NONE -> throw new IllegalStateException("Unreachable compensation state");
          }
          continue;
        }
        switch (current.placementPhase()) {
          case CREATED -> reserveRisk(current, surfaceRejection);
          case RISK_RESERVED -> confirmWallet(current, recovery);
          case WALLET_CONFIRMED -> commitRisk(current);
          case RISK_COMMITTED -> {
            return accept(current);
          }
        }
      } catch (DependencyUnavailableException unavailable) {
        return store.findById(betId);
      }
    }
    return store.findById(betId);
  }

  Bet reconcile(UUID betId) {
    return advance(betId, true, false);
  }

  private void confirmWallet(Bet bet, boolean recovery) {
    try {
      UUID operationId =
          recovery
              ? wallet
                  .findDebit(bet.betId(), bet.userId(), totalExposure(bet))
                  .map(WalletOperationResponse::operationGroupId)
                  .orElse(null)
              : null;
      if (operationId == null) {
        operationId = wallet.debit(bet.betId(), bet.userId(), totalExposure(bet));
      }
      store.confirmWallet(bet.betId(), operationId, clock.instant());
    } catch (InsufficientBalanceException | WalletRejectedException rejection) {
      store.requireRiskRelease(
          bet.betId(), rejection.errorCode(), rejection.getMessage(), clock.instant());
    }
  }

  private void commitRisk(Bet bet) {
    if (bet.riskCommitObserved()) {
      store.commitRisk(bet.betId(), clock.instant());
      return;
    }
    RiskClient.CommitResult result = risk.commit(bet.betId(), bet.riskReservationToken());
    if (result == RiskClient.CommitResult.COMMITTED) {
      store.commitRisk(bet.betId(), clock.instant());
      return;
    }
    ErrorCode code =
        result == RiskClient.CommitResult.CONFLICT
            ? ErrorCode.DUPLICATE_BET
            : ErrorCode.LIMIT_EXCEEDED;
    store.requireWalletRefund(
        bet.betId(), code, "Risk reservation commit failed: " + result, clock.instant());
  }

  private Bet accept(Bet bet) {
    OutboxEvent event = events.placedRequested(bet, clock.instant());
    Bet accepted = store.acceptAndEnqueue(bet.betId(), event, clock.instant());
    idempotency.markProcessed(IdempotencyKey.of(accepted.idempotencyKey()), accepted.betId());
    return accepted;
  }

  private void performCompensation(Bet bet) {
    if (bet.compensationAction() == CompensationAction.RISK_RELEASE) {
      RiskClient.ReleaseResult result = risk.release(bet.betId());
      store.completeRiskRelease(
          bet.betId(), result == RiskClient.ReleaseResult.COMMITTED, clock.instant());
      return;
    }
    if (bet.compensationAction() == CompensationAction.WALLET_REFUND) {
      UUID operationId = wallet.refund(bet.betId(), bet.userId(), totalExposure(bet));
      store.completeWalletRefund(bet.betId(), operationId, clock.instant());
      return;
    }
    throw new IllegalStateException("PENDING compensation has no action");
  }

  private Bet finishCompensatedRejection(Bet bet, boolean surfaceRejection) {
    Bet rejected = store.rejectAfterCompensation(bet.betId(), clock.instant());
    if (surfaceRejection) {
      return PlacementReplay.bet(rejected, rejected.userId(), rejected.requestFingerprint());
    }
    return rejected;
  }

  private void reserveRisk(Bet bet, boolean surfaceRejection) {
    try {
      Reservation reservation =
          risk.reserve(bet.betId(), bet.userId(), totalExposure(bet), selectionIds(bet.legs()));
      store.recordRiskReservation(
          bet.betId(),
          reservation.expiresAt(),
          reservation.token(),
          reservation.alreadyCommitted(),
          clock.instant());
    } catch (RiskLimitException | DuplicateBetException | ValidationFailedException rejection) {
      Bet rejected =
          store.rejectAtCreation(
              bet.betId(), rejection.errorCode(), rejection.getMessage(), clock.instant());
      if (surfaceRejection) {
        throw rejection;
      }
      if (rejected.status() == com.sportsbook.protocol.domain.BetStatus.PENDING) {
        throw new IllegalStateException("Risk rejection was not persisted");
      }
    }
  }

  private com.sportsbook.protocol.value.Money totalExposure(Bet bet) {
    return calculator.totalStake(bet.slipType(), bet.stake(), bet.legs().size());
  }

  private static List<UUID> selectionIds(List<BetLeg> legs) {
    return legs.stream().map(BetLeg::selectionId).toList();
  }

  private Bet replayKnown(
      String key,
      PlaceBetCommand command,
      String fingerprint,
      DataIntegrityViolationException collision) {
    Optional<PlacementRequest> request = store.findPlacementRequest(key);
    if (request.isPresent()) {
      return PlacementReplay.request(request.get(), command.userId(), fingerprint, store::findById);
    }
    Optional<Bet> legacyBet = store.findByIdempotencyKey(key);
    if (legacyBet.isPresent()) {
      return PlacementReplay.bet(legacyBet.get(), command.userId(), fingerprint);
    }
    throw collision;
  }

  private static boolean isDurablePreflight(BetPlacementException rejection) {
    return rejection instanceof ValidationFailedException
        || rejection instanceof MarketClosedException
        || rejection instanceof OddsDriftException;
  }
}
