package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.config.SettlementTopics;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SettlementStatus;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.event.StrictAvroDecoder;
import com.sportsbook.settlement.outbox.OutboxEvent;
import com.sportsbook.settlement.outbox.OutboxEventRepository;
import com.sportsbook.settlement.persistence.BetRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SettlementFinalizerTest {

  @Test
  void consumesExactLeaseAndCommitsStateWithOutbox() {
    BetRepository bets = mock(BetRepository.class);
    SettlementAttemptRepository attempts = mock(SettlementAttemptRepository.class);
    OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    SettlementFinalizer finalizer =
        new SettlementFinalizer(
            bets, attempts, outbox, new SettlementTopics(null, null, null, null, null, null));
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    Instant databaseNow = now.plusSeconds(1);
    UUID eventId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    Bet bet = pendingBet(eventId, selectionId, now);
    bet.applySelectionSnapshot(eventId, Map.of(selectionId, SettlementResult.WON), false, now);
    SettlementAttempt attempt = resolvedAttempt(bet.betId(), eventId, now);
    when(bets.findForUpdateById(bet.betId())).thenReturn(Optional.of(bet));
    when(attempts.consumeLease(attempt)).thenReturn(Optional.of(databaseNow));

    assertThat(finalizer.settle(attempt)).isTrue();

    assertThat(bet.status()).isEqualTo(SettlementStatus.SETTLED);
    assertThat(bet.payout()).isEqualTo(Money.krw(2000));
    assertThat(bet.settledAt()).isEqualTo(databaseNow);
    verify(attempts).consumeLease(attempt);
    ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outbox).save(event.capture());
    BetSettled decoded =
        new StrictAvroDecoder().decode(event.getValue().payload(), BetSettled.class);
    assertThat(decoded.getSettledAt()).isEqualTo(databaseNow);
  }

  private static Bet pendingBet(UUID eventId, UUID selectionId, Instant now) {
    BetSelection selection =
        new BetSelection(
            eventId, UUID.randomUUID(), selectionId, Odds.ofDecimal(new BigDecimal("2.0000")));
    return Bet.pending(
        UUID.randomUUID(),
        UUID.randomUUID(),
        SlipKind.SINGLE,
        null,
        null,
        new EmbeddedMoney(1000, Currency.KRW),
        now,
        List.of(selection),
        now);
  }

  private static SettlementAttempt resolvedAttempt(UUID betId, UUID eventId, Instant now) {
    return SettlementAttempt.resolved(
        betId,
        eventId,
        SettlementResult.WON,
        new SettlementMoneyPlan(
            Money.krw(1000), Money.krw(2000), Money.krw(1000), Money.krw(0), Money.krw(1000)),
        new SettlementLease(UUID.randomUUID(), Instant.MAX),
        now);
  }
}
