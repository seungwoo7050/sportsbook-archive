package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.client.RiskClient;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetDraft;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.domain.SystemBetCalculator;
import com.sportsbook.betting.error.PersistedRejectionException;
import com.sportsbook.protocol.domain.BetSlipType;
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
    BetPlacementService service = service(assembler, risk, store);

    catchThrowableOfType(() -> service.place(command), PersistedRejectionException.class);

    verifyNoInteractions(assembler, risk);
  }

  @Test
  void storesReservationTokenBeforeReturningFromReserveStep() {
    BetAssembler assembler = mock(BetAssembler.class);
    RiskClient risk = mock(RiskClient.class);
    BetStore store = mock(BetStore.class);
    PlaceBetCommand command = command();
    Bet bet = pending(command);
    Instant expiresAt = Instant.EPOCH.plusSeconds(60);
    when(store.findPlacementRequest("request-1")).thenReturn(Optional.empty());
    when(store.findByIdempotencyKey("request-1")).thenReturn(Optional.empty());
    when(assembler.assemble(eq(command), any(String.class))).thenReturn(bet);
    when(store.findById(bet.betId())).thenReturn(bet);
    when(risk.reserve(eq(bet.betId()), eq(bet.userId()), eq(Money.krw(1_000)), any()))
        .thenReturn(
            new RiskClient.Reservation(
                RiskClient.ReservationState.RESERVED, expiresAt, "b".repeat(64)));

    service(assembler, risk, store).place(command);

    InOrder order = inOrder(risk, store);
    order.verify(risk).reserve(eq(bet.betId()), eq(bet.userId()), eq(Money.krw(1_000)), any());
    order
        .verify(store)
        .recordRiskReservation(bet.betId(), expiresAt, "b".repeat(64), false, Instant.EPOCH);
  }

  @Test
  void persistsRiskValidationAfterPendingCreation() {
    BetAssembler assembler = mock(BetAssembler.class);
    RiskClient risk = mock(RiskClient.class);
    BetStore store = mock(BetStore.class);
    PlaceBetCommand command = command();
    Bet bet = pending(command);
    when(assembler.assemble(eq(command), any(String.class))).thenReturn(bet);
    when(store.findById(bet.betId())).thenReturn(bet);
    when(risk.reserve(any(), any(), any(), any()))
        .thenThrow(new com.sportsbook.betting.error.ValidationFailedException("invalid"));
    when(store.rejectAtCreation(any(), any(), any(), any())).thenReturn(bet);

    catchThrowableOfType(
        () -> service(assembler, risk, store).place(command),
        com.sportsbook.betting.error.ValidationFailedException.class);

    org.mockito.Mockito.verify(store)
        .rejectAtCreation(bet.betId(), ErrorCode.VALIDATION_FAILED, "invalid", Instant.EPOCH);
  }

  private static BetPlacementService service(
      BetAssembler assembler, RiskClient risk, BetStore store) {
    return new BetPlacementService(
        assembler,
        risk,
        new SystemBetCalculator(),
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

    BetPlacementService service = service(assembler, mock(RiskClient.class), store);
    catchThrowableOfType(
        () -> service.place(command),
        com.sportsbook.betting.error.ValidationFailedException.class);

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
