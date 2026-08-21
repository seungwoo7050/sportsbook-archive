package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskReservationServiceTest {
  private static final BetId BET = BetId.of(new UUID(0, 2));
  private static final Instant NOW = Instant.ofEpochMilli(2_000_000);

  @Test
  void delegatesTypedReservationOperations() {
    RiskReservationStore store = mock(RiskReservationStore.class);
    RiskReservationService service = new RiskReservationService(store);
    RiskCheckCommand command = command();
    ReservationDecision decision =
        ReservationDecision.approved(
            ReservationState.RESERVED, NOW.plusSeconds(60), "a".repeat(64), false, List.of());
    when(store.reserve(command)).thenReturn(decision);
    when(store.commit(BET, "a".repeat(64), NOW)).thenReturn(ReservationTransition.APPLIED);
    when(store.release(BET, NOW)).thenReturn(ReservationTransition.REPLAYED);

    assertThat(service.reserve(command)).isSameAs(decision);
    assertThat(service.commit(BET, "a".repeat(64), NOW)).isEqualTo(ReservationTransition.APPLIED);
    assertThat(service.release(BET, NOW)).isEqualTo(ReservationTransition.REPLAYED);
  }

  private static RiskCheckCommand command() {
    return new RiskCheckCommand(
        UserId.of(new UUID(0, 1)),
        BET,
        new Money(10, Currency.KRW),
        List.of(SelectionId.of(new UUID(0, 3))),
        NOW);
  }
}
