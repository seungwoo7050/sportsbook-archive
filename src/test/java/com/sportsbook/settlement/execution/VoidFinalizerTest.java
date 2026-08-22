package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.event.BetVoided;
import com.sportsbook.protocol.event.VoidReason;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VoidFinalizerTest {

  @Test
  void commitsWholeSlipVoidWithBetVoidedOutbox() {
    BetRepository bets = mock(BetRepository.class);
    SettlementAttemptRepository attempts = mock(SettlementAttemptRepository.class);
    OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    SettlementFinalizer finalizer =
        new SettlementFinalizer(
            bets, attempts, outbox, new SettlementTopics(null, null, null, null, null, null));
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    Instant databaseNow = now.plusSeconds(1);
    Bet bet = pendingBet(now);
    SettlementAttempt attempt =
        SettlementAttempt.wholeSlipVoid(
            bet.betId(),
            bet.selections().get(0).eventId(),
            "EVENT_CANCELLED",
            Money.krw(1000),
            new SettlementLease(UUID.randomUUID(), Instant.MAX),
            now);
    when(bets.findForUpdateById(bet.betId())).thenReturn(Optional.of(bet));
    when(attempts.consumeLease(attempt)).thenReturn(Optional.of(databaseNow));

    assertThat(finalizer.voidBet(attempt)).isTrue();

    assertThat(bet.status()).isEqualTo(SettlementStatus.VOIDED);
    ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outbox).save(event.capture());
    BetVoided decoded = new StrictAvroDecoder().decode(event.getValue().payload(), BetVoided.class);
    assertThat(decoded.getReason()).isEqualTo(VoidReason.EVENT_CANCELLED);
    assertThat(decoded.getVoidedAt()).isEqualTo(databaseNow);
  }

  private static Bet pendingBet(Instant now) {
    BetSelection selection =
        new BetSelection(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Odds.ofDecimal(new BigDecimal("2.0000")));
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
}
