package com.sportsbook.betting.client;

import com.sportsbook.protocol.value.Money;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RiskReservationRequest(
    String userId, String betId, Money stake, List<String> selectionIds) {

  public RiskReservationRequest {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(betId, "betId");
    Objects.requireNonNull(stake, "stake");
    selectionIds = List.copyOf(Objects.requireNonNull(selectionIds, "selectionIds"));
  }

  static RiskReservationRequest of(
      UUID betId, UUID userId, Money fullExposure, List<UUID> selectionIds) {
    return new RiskReservationRequest(
        userId.toString(),
        betId.toString(),
        fullExposure,
        selectionIds.stream().map(UUID::toString).toList());
  }
}
