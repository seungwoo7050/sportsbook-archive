package com.sportsbook.risk.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.policy.SafeRedisNumber;
import com.sportsbook.risk.service.RiskCheckCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

public record RiskCheckRequest(
    @NotNull UserId userId,
    @NotNull BetId betId,
    @NotNull Money stake,
    @NotEmpty @Size(max = RiskCheckCommand.MAX_SELECTIONS)
        List<@NotNull SelectionId> selectionIds) {

  public RiskCheckRequest {
    selectionIds = selectionIds == null ? null : List.copyOf(selectionIds);
  }

  @JsonIgnore
  @AssertTrue(message = "stake amount must be positive and exactly representable")
  public boolean hasValidStakeAmount() {
    if (stake == null) {
      return true;
    }
    try {
      SafeRedisNumber.requirePositive(stake.amount(), "stake.amount");
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  @JsonIgnore
  @AssertTrue(message = "selectionIds must be unique")
  public boolean hasUniqueSelections() {
    return selectionIds == null || new HashSet<>(selectionIds).size() == selectionIds.size();
  }

  RiskCheckCommand toCommand(Instant now) {
    return new RiskCheckCommand(userId, betId, stake, selectionIds, now);
  }
}
