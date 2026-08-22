package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.client.RiskClient;
import com.sportsbook.betting.client.WalletClient;
import com.sportsbook.betting.client.WalletOperationResponse;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetDraft;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.domain.SystemBetCalculator;
import com.sportsbook.betting.error.PersistedRejectionException;
import com.sportsbook.betting.outbox.BetEventFactory;
import com.sportsbook.betting.outbox.OutboxEvent;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.error.ErrorCode;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class BetPlacementServiceTest {

  @Test
  void replaysOwnedVerdictBeforeRepeatingValidationOrSideEffects() {
    BetAssembler assembler = mock(BetAssembler.class);
    RiskClient risk = mock(RiskClient.class);
    WalletClient wallet = mock(WalletClient.class);
    BetEventFactory events = mock(BetEventFactory.class);
    IdempotencyCache idempotency = mock(IdempotencyCache.class);
    BetStore store = mock(BetStore.class);
    PlaceBetCommand command = command();
    PlacementRequest request =
        PlacementRequest.rejected(
            "request-1",
            command.userId(),
            RequestFingerprint.of(command),
            ErrorCode.VALIDATION_FAILED,
            "saved verdict",
            Instant.EPOCH);
    when(store.findPlacementRequest("request-1")).thenReturn(Optional.of(request));

    catchThrowableOfType(
        () -> service(assembler, risk, wallet, events, idempotency, store).place(command),
        PersistedRejectionException.class);

    verifyNoInteractions(assembler, risk, wallet);
  }

  @Test
  void persistsReservationProofBeforeWalletDebit() {
    BetAssembler assembler = mock(BetAssembler.class);
    RiskClient risk = mock(RiskClient.class);
    WalletClient wallet = mock(WalletClient.class);
    BetEventFactory events = mock(BetEventFactory.class);
    IdempotencyCache idempotency = mock(IdempotencyCache.class);
    BetStore store = mock(BetStore.class);
    PlaceBetCommand command = command();
    Bet bet = pending(command);
    Instant expiresAt = Instant.EPOCH.plusSeconds(60);
    UUID operationId = UUID.randomUUID();
    when(store.findPlacementRequest("request-1")).thenReturn(Optional.empty());
    when(store.findByIdempotencyKey("request-1")).thenReturn(Optional.empty());
    when(assembler.assemble(eq(command), any(String.class))).thenReturn(bet);
    when(store.findById(bet.betId())).thenReturn(bet);
    when(risk.reserve(eq(bet.betId()), eq(bet.userId()), eq(Money.krw(1_000)), any()))
        .thenReturn(
            new RiskClient.Reservation(
                RiskClient.ReservationState.RESERVED, expiresAt, "b".repeat(64)));
    doAnswer(
            ignored -> {
              bet.recordRiskReservation(expiresAt, "b".repeat(64), false, Instant.EPOCH);
              return null;
            })
        .when(store)
        .recordRiskReservation(bet.betId(), expiresAt, "b".repeat(64), false, Instant.EPOCH);
    when(wallet.debit(bet.betId(), bet.userId(), Money.krw(1_000))).thenReturn(operationId);
    doAnswer(
            ignored -> {
              bet.confirmWallet(operationId, Instant.EPOCH);
              return null;
            })
        .when(store)
        .confirmWallet(bet.betId(), operationId, Instant.EPOCH);
    when(risk.commit(bet.betId(), "b".repeat(64))).thenReturn(RiskClient.CommitResult.COMMITTED);
    doAnswer(
            ignored -> {
              bet.commitRisk(Instant.EPOCH);
              return null;
            })
        .when(store)
        .commitRisk(bet.betId(), Instant.EPOCH);
    OutboxEvent event =
        OutboxEvent.pending(
            UUID.randomUUID(),
            "bet.placed.v1",
            bet.userId().toString(),
            "schema",
            new byte[] {1},
            Instant.EPOCH);
    when(events.placedRequested(bet, Instant.EPOCH)).thenReturn(event);
    doAnswer(
            ignored -> {
              bet.accept(Instant.EPOCH);
              return bet;
            })
        .when(store)
        .acceptAndEnqueue(bet.betId(), event, Instant.EPOCH);

    service(assembler, risk, wallet, events, idempotency, store).place(command);

    InOrder order = inOrder(risk, store, wallet);
    order.verify(risk).reserve(eq(bet.betId()), eq(bet.userId()), eq(Money.krw(1_000)), any());
    order
        .verify(store)
        .recordRiskReservation(bet.betId(), expiresAt, "b".repeat(64), false, Instant.EPOCH);
    order.verify(wallet).debit(bet.betId(), bet.userId(), Money.krw(1_000));
    order.verify(store).confirmWallet(bet.betId(), operationId, Instant.EPOCH);
    order.verify(risk).commit(bet.betId(), "b".repeat(64));
    order.verify(store).commitRisk(bet.betId(), Instant.EPOCH);
    order.verify(store).acceptAndEnqueue(bet.betId(), event, Instant.EPOCH);
    InOrder completion = inOrder(store, idempotency);
    completion.verify(store).acceptAndEnqueue(bet.betId(), event, Instant.EPOCH);
    completion.verify(idempotency).markProcessed(IdempotencyKey.of("request-1"), bet.betId());
  }

  @Test
  void persistsRiskValidationAfterPendingCreation() {
    BetAssembler assembler = mock(BetAssembler.class);
    RiskClient risk = mock(RiskClient.class);
    WalletClient wallet = mock(WalletClient.class);
    BetStore store = mock(BetStore.class);
    PlaceBetCommand command = command();
    Bet bet = pending(command);
    when(assembler.assemble(eq(command), any(String.class))).thenReturn(bet);
    when(store.findById(bet.betId())).thenReturn(bet);
    when(risk.reserve(any(), any(), any(), any()))
        .thenThrow(new com.sportsbook.betting.error.ValidationFailedException("invalid"));
    when(store.rejectAtCreation(any(), any(), any(), any())).thenReturn(bet);

    catchThrowableOfType(
        () -> service(assembler, risk, wallet, mock(), mock(), store).place(command),
        com.sportsbook.betting.error.ValidationFailedException.class);

    org.mockito.Mockito.verify(store)
        .rejectAtCreation(bet.betId(), ErrorCode.VALIDATION_FAILED, "invalid", Instant.EPOCH);
    verifyNoInteractions(wallet);
  }

  @Test
  void recoversWalletDebitWithExpectedSemantics() {
    RiskClient risk = mock(RiskClient.class);
    WalletClient wallet = mock(WalletClient.class);
    BetStore store = mock(BetStore.class);
    Bet bet = pending(command());
    bet.recordRiskReservation(Instant.EPOCH.plusSeconds(60), "f".repeat(64), false, Instant.EPOCH);
    UUID operationId = UUID.randomUUID();
    when(store.findById(bet.betId())).thenReturn(bet);
    when(wallet.findDebit(bet.betId(), bet.userId(), Money.krw(1_000)))
        .thenReturn(
            Optional.of(
                new WalletOperationResponse(
                    operationId, bet.userId(), Money.krw(1_000), "BET_DEBIT", Instant.EPOCH)));
    doAnswer(
            ignored -> {
              bet.confirmWallet(operationId, Instant.EPOCH);
              return null;
            })
        .when(store)
        .confirmWallet(bet.betId(), operationId, Instant.EPOCH);
    when(risk.commit(bet.betId(), "f".repeat(64)))
        .thenThrow(new com.sportsbook.betting.error.DependencyUnavailableException("offline"));

    service(mock(), risk, wallet, mock(), mock(), store).reconcile(bet.betId());

    org.mockito.Mockito.verify(wallet).findDebit(bet.betId(), bet.userId(), Money.krw(1_000));
    org.mockito.Mockito.verify(wallet, org.mockito.Mockito.never()).debit(any(), any(), any());
  }

  @Test
  void isolatesMismatchedRecoveredDebitWithoutRefundingIt() {
    WalletClient wallet = mock(WalletClient.class);
    BetStore store = mock(BetStore.class);
    Bet bet = pending(command());
    bet.recordRiskReservation(Instant.EPOCH.plusSeconds(60), "a".repeat(64), false, Instant.EPOCH);
    when(store.findById(bet.betId())).thenReturn(bet);
    when(wallet.findDebit(bet.betId(), bet.userId(), Money.krw(1_000)))
        .thenThrow(
            new com.sportsbook.betting.error.WalletRejectedException(
                "WALLET_OPERATION_MISMATCH", "mismatch"));
    doAnswer(
            ignored -> {
              bet.requireRiskRelease("VALIDATION_FAILED", "mismatch", Instant.EPOCH);
              return null;
            })
        .when(store)
        .requireRiskRelease(any(), any(), any(), any());

    service(mock(), mock(), wallet, mock(), mock(), store).reconcile(bet.betId());

    org.mockito.Mockito.verify(store)
        .requireRiskRelease(bet.betId(), ErrorCode.VALIDATION_FAILED, "mismatch", Instant.EPOCH);
    org.mockito.Mockito.verify(wallet, org.mockito.Mockito.never()).refund(any(), any(), any());
  }

  @Test
  void refundsWithoutRereservingWhenRiskCommitIsMissing() {
    BetAssembler assembler = mock(BetAssembler.class);
    RiskClient risk = mock(RiskClient.class);
    WalletClient wallet = mock(WalletClient.class);
    BetEventFactory events = mock(BetEventFactory.class);
    IdempotencyCache idempotency = mock(IdempotencyCache.class);
    BetStore store = mock(BetStore.class);
    Bet bet = pending(command());
    bet.recordRiskReservation(Instant.EPOCH.plusSeconds(60), "c".repeat(64), false, Instant.EPOCH);
    bet.confirmWallet(UUID.randomUUID(), Instant.EPOCH);
    when(store.findById(bet.betId())).thenReturn(bet);
    when(risk.commit(bet.betId(), "c".repeat(64))).thenReturn(RiskClient.CommitResult.NOT_FOUND);
    doAnswer(
            ignored -> {
              bet.requireWalletRefund("LIMIT_EXCEEDED", "missing reservation", Instant.EPOCH);
              return null;
            })
        .when(store)
        .requireWalletRefund(
            bet.betId(),
            ErrorCode.LIMIT_EXCEEDED,
            "Risk reservation commit failed: NOT_FOUND",
            Instant.EPOCH);
    checkpointCompensation(store, bet);
    UUID refundId = UUID.randomUUID();
    when(wallet.refund(bet.betId(), bet.userId(), Money.krw(1_000))).thenReturn(refundId);
    doAnswer(
            ignored -> {
              bet.completeWalletRefund(refundId, Instant.EPOCH);
              return null;
            })
        .when(store)
        .completeWalletRefund(bet.betId(), refundId, Instant.EPOCH);
    rejectAfterCompensation(store, bet);
    Bet result =
        service(assembler, risk, wallet, events, idempotency, store).reconcile(bet.betId());

    assertThat(result.status()).isEqualTo(BetStatus.REJECTED);
    org.mockito.Mockito.verify(risk, org.mockito.Mockito.never())
        .reserve(any(), any(), any(), any());
  }

  private static void checkpointCompensation(BetStore store, Bet bet) {
    doAnswer(
            ignored -> {
              bet.beginCompensation(Instant.EPOCH);
              return null;
            })
        .when(store)
        .beginCompensation(bet.betId(), Instant.EPOCH);
  }

  private static void rejectAfterCompensation(BetStore store, Bet bet) {
    doAnswer(
            ignored -> {
              bet.rejectAfterCompensation(Instant.EPOCH);
              return bet;
            })
        .when(store)
        .rejectAfterCompensation(bet.betId(), Instant.EPOCH);
  }

  private static BetPlacementService service(
      BetAssembler assembler,
      RiskClient risk,
      WalletClient wallet,
      BetEventFactory events,
      IdempotencyCache idempotency,
      BetStore store) {
    return new BetPlacementService(
        assembler,
        risk,
        wallet,
        new SystemBetCalculator(),
        events,
        idempotency,
        store,
        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
  }

  @Test
  void persistsDuplicateSelectionValidationBeforeCreatingABet() {
    BetAssembler assembler = mock(BetAssembler.class);
    BetStore store = mock(BetStore.class);
    PlaceBetCommand command = command();
    when(assembler.assemble(eq(command), any(String.class)))
        .thenThrow(
            new com.sportsbook.betting.error.ValidationFailedException(
                "Duplicate selection is not allowed"));

    BetPlacementService service =
        service(
            assembler,
            mock(RiskClient.class),
            mock(WalletClient.class),
            mock(BetEventFactory.class),
            mock(IdempotencyCache.class),
            store);
    catchThrowableOfType(
        () -> service.place(command), com.sportsbook.betting.error.ValidationFailedException.class);

    org.mockito.Mockito.verify(store)
        .savePreflightRejection(
            eq("request-1"),
            eq(command.userId()),
            any(String.class),
            eq(ErrorCode.VALIDATION_FAILED),
            eq("Duplicate selection is not allowed"),
            eq(Instant.EPOCH));
    org.mockito.Mockito.verify(store, org.mockito.Mockito.never()).savePending(any());
  }

  private static PlaceBetCommand command() {
    return new PlaceBetCommand(
        UUID.randomUUID(),
        new BetSlipType.Single(),
        List.of(
            new PlaceBetCommand.SelectionInput(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2"))),
        Money.krw(1_000),
        IdempotencyKey.of("request-1"));
  }

  private static Bet pending(PlaceBetCommand command) {
    BetDraft draft =
        new BetDraft(
            UUID.randomUUID(),
            command.userId(),
            "B-2026-08-22-00000000",
            command.slipType(),
            command.unitStake(),
            Money.krw(2_000),
            command.idempotencyKey(),
            RequestFingerprint.of(command),
            Instant.EPOCH);
    PlaceBetCommand.SelectionInput input = command.selections().get(0);
    BetLeg leg =
        BetLeg.create(
            input.eventId(), input.marketId(), input.selectionId(), input.oddsAtSubmission());
    return Bet.pending(draft, List.of(leg));
  }
}
