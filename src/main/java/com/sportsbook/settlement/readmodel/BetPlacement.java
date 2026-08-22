package com.sportsbook.settlement.readmodel;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Strictly decoded, Avro-free placement snapshot consumed by persistence. */
public record BetPlacement(
    UUID betId,
    UUID userId,
    BetSlipType slipType,
    Money unitStake,
    Instant requestedAt,
    List<Selection> selections) {

  public BetPlacement {
    Objects.requireNonNull(betId, "betId");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(slipType, "slipType");
    Objects.requireNonNull(unitStake, "unitStake");
    Objects.requireNonNull(requestedAt, "requestedAt");
    selections = List.copyOf(Objects.requireNonNull(selections, "selections"));
  }

  public record Selection(UUID eventId, UUID marketId, UUID selectionId, Odds odds) {

    public Selection {
      Objects.requireNonNull(eventId, "eventId");
      Objects.requireNonNull(marketId, "marketId");
      Objects.requireNonNull(selectionId, "selectionId");
      Objects.requireNonNull(odds, "odds");
    }
  }
}
