package com.sportsbook.settlement.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.execution.SettlementAttemptDraft;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementLease;
import com.sportsbook.settlement.persistence.BetRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResolvedResultPreparationTest {

  @Test
  void claimsVoidedMatchResultsThroughTheNormalSettlementPath() {
    UUID eventId = UUID.randomUUID();
    BetSelection selection =
        new BetSelection(eventId, UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
    Bet bet =
        Bet.pending(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SlipKind.SINGLE,
            null,
            null,
            EmbeddedMoney.of(Money.krw(100)),
            Instant.EPOCH,
            List.of(selection),
            Instant.EPOCH);
    BetRepository bets = mock(BetRepository.class);
    SettlementAttemptRepository attempts = mock(SettlementAttemptRepository.class);
    when(bets.findForUpdateById(bet.betId())).thenReturn(Optional.of(bet));
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
    var preparer =
        new ResultSettlementPreparer(
            bets,
            attempts,
            new BaseSettlementPlanner(),
            new SettlementRuntimeProperties(null, null, null, 0),
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    AcceptedResult accepted =
        new AcceptedResult(
            eventId, UUID.randomUUID(), MatchOutcomeMode.VOIDED, Map.of(), Instant.EPOCH);

    var execution = preparer.prepare(bet.betId(), accepted).orElseThrow();

    assertThat(execution.attempt().result()).isEqualTo(SettlementResult.VOID);
    assertThat(execution.attempt().action())
        .isEqualTo(com.sportsbook.settlement.execution.SettlementAttempt.Action.SETTLE);
    assertThat(execution.attempt().money().payout()).isEqualTo(Money.krw(100));
    assertThat(selection.sourceCandidateId()).isEqualTo(accepted.candidateId());
  }
}
