package com.sportsbook.betting.outbox;

import com.sportsbook.betting.config.BettingTopics;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.infrastructure.id.UuidV7;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.BetSlipTypeTag;
import com.sportsbook.protocol.event.RequestedSelection;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class BetEventFactory {

  public OutboxEvent placedRequested(Bet bet, Instant now) {
    BetSlipType type = bet.slipType();
    BetPlacedRequested record =
        BetPlacedRequested.newBuilder()
            .setBetId(bet.betId().toString())
            .setUserId(bet.userId().toString())
            .setSlipType(tag(type))
            .setSystemMinWins(type instanceof BetSlipType.System system ? system.minWins() : null)
            .setSystemTotalSelections(
                type instanceof BetSlipType.System system ? system.totalSelections() : null)
            .setSelections(
                bet.legs().stream()
                    .map(
                        leg ->
                            RequestedSelection.newBuilder()
                                .setEventId(leg.eventId().toString())
                                .setMarketId(leg.marketId().toString())
                                .setSelectionId(leg.selectionId().toString())
                                .setOddsAtSubmission(
                                    leg.oddsAtSubmission().decimal().toPlainString())
                                .build())
                    .toList())
            .setStake(
                com.sportsbook.protocol.event.Money.newBuilder()
                    .setAmount(bet.stake().amount())
                    .setCurrency(bet.stake().currency().name())
                    .build())
            .setIdempotencyKey(bet.idempotencyKey())
            .setRequestedAt(bet.createdAt())
            .build();
    return OutboxEvent.pending(
        UuidV7.generate(),
        BettingTopics.BET_PLACED,
        bet.userId().toString(),
        BetPlacedRequested.getClassSchema().getName(),
        AvroSerializer.serialize(record),
        now);
  }

  private static BetSlipTypeTag tag(BetSlipType type) {
    if (type instanceof BetSlipType.Single) {
      return BetSlipTypeTag.SINGLE;
    }
    if (type instanceof BetSlipType.Multiple) {
      return BetSlipTypeTag.MULTIPLE;
    }
    return BetSlipTypeTag.SYSTEM;
  }
}
