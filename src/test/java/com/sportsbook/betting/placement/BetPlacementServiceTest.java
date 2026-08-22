package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

class BetPlacementServiceTest {

  @Test
  void replaysOwnedVerdictBeforeRepeatingValidationOrSideEffects() {
    BetAssembler assembler = mock(BetAssembler.class);
    BetStore store = mock(BetStore.class);
    PlaceBetCommand command = command();
    String fingerprint = RequestFingerprint.of(command);
    PlacementRequest request =
        PlacementRequest.rejected(
            "request-1",
            command.userId(),
            fingerprint,
            ErrorCode.VALIDATION_FAILED,
            "saved verdict",
            Instant.EPOCH);
    when(store.findPlacementRequest("request-1")).thenReturn(Optional.of(request));
    BetPlacementService service =
        new BetPlacementService(assembler, store, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    catchThrowableOfType(() -> service.place(command), PersistedRejectionException.class);

    verifyNoInteractions(assembler);
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
        new BetPlacementService(assembler, store, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
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
}
