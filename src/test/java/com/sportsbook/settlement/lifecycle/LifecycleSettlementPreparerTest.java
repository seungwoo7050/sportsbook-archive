package com.sportsbook.settlement.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.execution.SettlementAttemptDraft;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementLease;
import com.sportsbook.settlement.persistence.BetRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LifecycleSettlementPreparerTest {

  @Test
  void locksAndClaimsTheFullSystemExposureWithDatabaseTime() {
    BetRepository bets = mock(BetRepository.class);
    SettlementAttemptRepository attempts = mock(SettlementAttemptRepository.class);
    Bet bet = mock(Bet.class);
    UUID betId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    when(bet.betId()).thenReturn(betId);
    when(bet.userId()).thenReturn(UUID.randomUUID());
    when(bet.stake()).thenReturn(Money.krw(100));
    when(bet.slipType()).thenReturn(new BetSlipType.System(2, 3));
    when(bet.status()).thenReturn(com.sportsbook.settlement.domain.SettlementStatus.PENDING);
    when(bets.findForUpdateById(betId)).thenReturn(Optional.of(bet));
    when(attempts.claimPending(any(), eq(Duration.ofSeconds(30))))
        .thenAnswer(
            invocation -> {
              SettlementAttemptDraft draft = invocation.getArgument(0);
              return Optional.of(
                  draft.claimed(
                      new SettlementLease(UUID.randomUUID(), Instant.EPOCH.plusSeconds(30)),
                      Instant.EPOCH,
                      Instant.EPOCH));
            });

    var execution =
        new LifecycleSettlementPreparer(
                bets, attempts, new SettlementRuntimeProperties(null, null, null, 0))
            .prepare(betId, eventId, "EVENT_CANCELLED")
            .orElseThrow();

    assertThat(execution.attempt().money().committed()).isEqualTo(Money.krw(300));
    assertThat(execution.attempt().voidReason()).isEqualTo("EVENT_CANCELLED");
    assertThat(execution.attempt().attemptCount()).isEqualTo(1);
  }
}
