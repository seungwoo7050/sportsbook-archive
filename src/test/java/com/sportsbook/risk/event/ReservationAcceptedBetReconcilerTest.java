package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.reservation.ReservationFingerprint;
import com.sportsbook.risk.reservation.ReservationTransition;
import com.sportsbook.risk.reservation.RiskReservationStore;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationAcceptedBetReconcilerTest {
  private static final Instant NOW = Instant.ofEpochMilli(2_000_000);
  private final RiskCheckCommand command =
      new RiskCheckCommand(
          UserId.of(new UUID(0, 1)),
          BetId.of(new UUID(0, 2)),
          new Money(10, Currency.KRW),
          List.of(SelectionId.of(new UUID(0, 3))),
          NOW);
  private final AcceptedBetEnvelope envelope = new AcceptedBetEnvelope(command, NOW);
  private final String fingerprint = ReservationFingerprint.of(command);
  private final RiskReservationStore store = mock(RiskReservationStore.class);
  private final ReservationAcceptedBetReconciler reconciler =
      new ReservationAcceptedBetReconciler(store);

  @Test
  void projectsOnlyWhenTheReservationIsNotFound() {
    when(store.commit(command.betId(), fingerprint, NOW))
        .thenReturn(ReservationTransition.NOT_FOUND);
    when(store.projectAccepted(command, fingerprint)).thenReturn(ReservationTransition.APPLIED);

    assertThat(reconciler.reconcile(envelope)).isEqualTo(AcceptedBetReconciliation.PROJECTED);
    verify(store).projectAccepted(command, fingerprint);
  }

  @Test
  void mapsProjectionReplayAndConflict() {
    when(store.commit(command.betId(), fingerprint, NOW))
        .thenReturn(ReservationTransition.NOT_FOUND);
    when(store.projectAccepted(command, fingerprint))
        .thenReturn(ReservationTransition.REPLAYED, ReservationTransition.CONFLICT);

    assertThat(reconciler.reconcile(envelope)).isEqualTo(AcceptedBetReconciliation.REPLAYED);
    assertThat(reconciler.reconcile(envelope))
        .isEqualTo(AcceptedBetReconciliation.FINGERPRINT_MISMATCH);
  }

  @Test
  void mapsReservedLifecycleOutcomesWithoutProjection() {
    when(store.commit(command.betId(), fingerprint, NOW))
        .thenReturn(
            ReservationTransition.APPLIED,
            ReservationTransition.REPLAYED,
            ReservationTransition.CONFLICT,
            ReservationTransition.EXPIRED);

    assertThat(reconciler.reconcile(envelope)).isEqualTo(AcceptedBetReconciliation.CONFIRMED);
    assertThat(reconciler.reconcile(envelope)).isEqualTo(AcceptedBetReconciliation.REPLAYED);
    assertThat(reconciler.reconcile(envelope))
        .isEqualTo(AcceptedBetReconciliation.FINGERPRINT_MISMATCH);
    assertThat(reconciler.reconcile(envelope))
        .isEqualTo(AcceptedBetReconciliation.TERMINAL_RESERVATION);
    verify(store, never()).projectAccepted(command, fingerprint);
  }
}
