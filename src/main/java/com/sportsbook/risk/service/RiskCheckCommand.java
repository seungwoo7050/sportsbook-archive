package com.sportsbook.risk.service;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.policy.SafeRedisNumber;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Fully validated candidate shared by diagnostic, reservation, and event paths. */
public record RiskCheckCommand(
    UserId userId, BetId betId, Money stake, List<SelectionId> selectionIds, Instant now) {

  public static final int MAX_SELECTIONS = 15;

  public RiskCheckCommand {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(betId, "betId");
    Objects.requireNonNull(stake, "stake");
    Objects.requireNonNull(selectionIds, "selectionIds");
    Objects.requireNonNull(now, "now");
    SafeRedisNumber.requirePositive(stake.amount(), "stake.amount");
    if (selectionIds.isEmpty() || selectionIds.size() > MAX_SELECTIONS) {
      throw new IllegalArgumentException(
          "selectionIds size must be between 1 and " + MAX_SELECTIONS);
    }
    if (selectionIds.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("selectionIds must not contain null");
    }
    if (new HashSet<>(selectionIds).size() != selectionIds.size()) {
      throw new IllegalArgumentException("selectionIds must be unique");
    }
    selectionIds = List.copyOf(selectionIds);
  }
}
