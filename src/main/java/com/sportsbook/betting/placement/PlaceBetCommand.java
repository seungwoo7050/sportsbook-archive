package com.sportsbook.betting.placement;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlaceBetCommand(
    UUID userId,
    BetSlipType slipType,
    List<SelectionInput> selections,
    Money unitStake,
    IdempotencyKey idempotencyKey) {

  public PlaceBetCommand {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(slipType, "slipType");
    Objects.requireNonNull(unitStake, "unitStake");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    selections = List.copyOf(Objects.requireNonNull(selections, "selections"));
  }

  public record SelectionInput(
      UUID eventId, UUID marketId, UUID selectionId, Odds oddsAtSubmission) {

    public SelectionInput {
      Objects.requireNonNull(eventId, "eventId");
      Objects.requireNonNull(marketId, "marketId");
      Objects.requireNonNull(selectionId, "selectionId");
      Objects.requireNonNull(oddsAtSubmission, "oddsAtSubmission");
    }
  }
}
