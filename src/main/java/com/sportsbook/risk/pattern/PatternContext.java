package com.sportsbook.risk.pattern;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable candidate facts shared by every pattern rule. */
public record PatternContext(
    UserId userId, BetId betId, Money stake, List<SelectionId> selections, Instant evaluatedAt) {
  public PatternContext {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(betId, "betId");
    Objects.requireNonNull(stake, "stake");
    Objects.requireNonNull(selections, "selections");
    Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    if (selections.isEmpty()) {
      throw new IllegalArgumentException("selections must not be empty");
    }
    selections = List.copyOf(selections);
  }

  public static PatternContext from(RiskCheckCommand command) {
    Objects.requireNonNull(command, "command");
    return new PatternContext(
        command.userId(), command.betId(), command.stake(), command.selectionIds(), command.now());
  }
}
