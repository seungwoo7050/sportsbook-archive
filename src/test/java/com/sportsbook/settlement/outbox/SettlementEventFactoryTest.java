package com.sportsbook.settlement.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.BetVoided;
import com.sportsbook.protocol.event.SettlementResultAvro;
import com.sportsbook.protocol.event.VoidReason;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.config.SettlementTopics;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.event.StrictAvroDecoder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementEventFactoryTest {

  @Test
  void emitsStoredUnitStakeInsteadOfSystemTotalExposure() {
    UUID drivingEventId = UUID.randomUUID();
    List<BetSelection> selections =
        List.of(selection(drivingEventId), selection(drivingEventId), selection(drivingEventId));
    Bet bet =
        Bet.pending(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SlipKind.SYSTEM,
            2,
            3,
            new EmbeddedMoney(1_000, Currency.KRW),
            Instant.EPOCH,
            selections,
            Instant.EPOCH);
    Map<UUID, SettlementResult> outcomes =
        Map.of(
            selections.get(0).selectionId(), SettlementResult.WON,
            selections.get(1).selectionId(), SettlementResult.WON,
            selections.get(2).selectionId(), SettlementResult.WON);
    bet.applySelectionSnapshot(drivingEventId, outcomes, true, Instant.EPOCH);
    bet.recordSettled(SettlementResult.WON, Money.krw(26_000), Instant.EPOCH);

    SettlementTopics topics = new SettlementTopics(null, null, null, null, null, null);
    OutboxEvent outbox =
        new SettlementEventFactory(topics, new StrictAvroEncoder()).settled(bet, drivingEventId);
    BetSettled event = new StrictAvroDecoder().decode(outbox.payload(), BetSettled.class);

    assertThat(outbox.topic()).isEqualTo("bet.settled.v1");
    assertThat(outbox.partitionKey()).isEqualTo(drivingEventId.toString());
    assertThat(event.getResult()).isEqualTo(SettlementResultAvro.WON);
    assertThat(event.getStake().getAmount()).isEqualTo(1_000);
    assertThat(event.getPayout().getAmount()).isEqualTo(26_000);
    assertThat(event.getResultDetail()).hasSize(3);
  }

  @Test
  void emitsWholeSlipExposureForLifecycleVoidRefunds() {
    UUID eventId = UUID.randomUUID();
    Bet bet =
        Bet.pending(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SlipKind.SYSTEM,
            2,
            3,
            new EmbeddedMoney(1_000, Currency.KRW),
            Instant.EPOCH,
            List.of(selection(eventId), selection(eventId), selection(eventId)),
            Instant.EPOCH);
    bet.recordVoided(Money.krw(3_000), Instant.EPOCH);
    SettlementEventFactory factory =
        new SettlementEventFactory(
            new SettlementTopics(null, null, null, null, null, null), new StrictAvroEncoder());

    OutboxEvent outbox = factory.voided(bet, eventId, VoidReason.EVENT_CANCELLED);
    BetVoided event = new StrictAvroDecoder().decode(outbox.payload(), BetVoided.class);

    assertThat(outbox.topic()).isEqualTo("bet.voided.v1");
    assertThat(event.getReason()).isEqualTo(VoidReason.EVENT_CANCELLED);
    assertThat(event.getRefund().getAmount()).isEqualTo(3_000);
  }

  private static BetSelection selection(UUID eventId) {
    return new BetSelection(
        eventId, UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
  }
}
