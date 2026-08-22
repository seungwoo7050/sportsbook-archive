package com.sportsbook.betting.validation;

import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.error.ValidationFailedException;
import com.sportsbook.betting.policy.BettingPolicyProperties;
import com.sportsbook.protocol.domain.BetSlipType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BetSlipValidator {

  private final BettingPolicyProperties policy;

  public BetSlipValidator(BettingPolicyProperties policy) {
    this.policy = policy;
  }

  public void validate(BetSlipType type, List<BetLeg> legs) {
    if (legs.isEmpty()) {
      throw new ValidationFailedException("Slip must contain at least one selection");
    }
    if (legs.size() > policy.maxSelections()) {
      throw new ValidationFailedException("Maximum selections exceeded");
    }
    requireDistinctSelections(legs);
    if (!(type instanceof BetSlipType.Single)) {
      requireDistinctMarketsAndEvents(legs);
    }
  }

  private static void requireDistinctMarketsAndEvents(List<BetLeg> legs) {
    Set<UUID> markets = new HashSet<>();
    Set<UUID> events = new HashSet<>();
    for (BetLeg leg : legs) {
      if (!markets.add(leg.marketId())) {
        throw new ValidationFailedException("Same market is not allowed in a multi");
      }
      if (!events.add(leg.eventId())) {
        throw new ValidationFailedException("Same event is not allowed in a multi");
      }
    }
  }

  private static void requireDistinctSelections(List<BetLeg> legs) {
    Set<UUID> selections = new HashSet<>();
    for (BetLeg leg : legs) {
      if (!selections.add(leg.selectionId())) {
        throw new ValidationFailedException("Duplicate selection is not allowed");
      }
    }
  }
}
