package com.sportsbook.settlement.outbox;

import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.SettlementResultAvro;
import com.sportsbook.settlement.config.SettlementTopics;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.SettlementStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Creates fixed wire-v1 settlement records and their durable outbox envelopes. */
public final class SettlementEventFactory {

  private final SettlementTopics topics;
  private final StrictAvroEncoder encoder;

  public SettlementEventFactory(SettlementTopics topics, StrictAvroEncoder encoder) {
    this.topics = topics;
    this.encoder = encoder;
  }

  public OutboxEvent settled(Bet bet, UUID drivingEventId) {
    if (bet.status() != SettlementStatus.SETTLED || bet.result() == null || bet.payout() == null) {
      throw new IllegalStateException("Bet must be SETTLED before creating BetSettled");
    }
    BetSettled event =
        BetSettled.newBuilder()
            .setBetId(bet.betId().toString())
            .setUserId(bet.userId().toString())
            .setEventId(drivingEventId.toString())
            .setResult(SettlementResultAvro.valueOf(bet.result().name()))
            .setStake(money(bet.stake()))
            .setPayout(money(bet.payout()))
            .setSettledAt(bet.settledAt())
            .setResultDetail(resultDetail(bet))
            .build();
    return OutboxEvent.pending(
        topics.betSettled(),
        drivingEventId.toString(),
        BetSettled.class.getSimpleName(),
        encoder.encode(event),
        bet.settledAt());
  }

  private static com.sportsbook.protocol.event.Money money(
      com.sportsbook.protocol.value.Money value) {
    return com.sportsbook.protocol.event.Money.newBuilder()
        .setAmount(value.amount())
        .setCurrency(value.currency().name())
        .build();
  }

  private static Map<String, String> resultDetail(Bet bet) {
    Map<String, String> detail = new LinkedHashMap<>();
    for (BetSelection selection : bet.selections()) {
      if (selection.outcome() == null) {
        throw new IllegalStateException("BetSettled requires every selection outcome");
      }
      detail.put(selection.selectionId().toString(), selection.outcome().name());
    }
    return Map.copyOf(detail);
  }
}
