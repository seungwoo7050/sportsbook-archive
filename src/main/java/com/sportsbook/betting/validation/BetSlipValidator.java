package com.sportsbook.betting.validation;

import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.error.ValidationFailedException;
import com.sportsbook.betting.policy.BettingPolicyProperties;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import java.math.BigDecimal;
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
    requireTotalOdds(legs);
  }

  public void validateStake(Money stake) {
    Long minimum = policy.minStake().get(stake.currency());
    Long maximum = policy.maxStake().get(stake.currency());
    if (minimum == null || maximum == null) {
      throw new ValidationFailedException("Unsupported stake currency");
    }
    if (stake.amount() < minimum || stake.amount() > maximum) {
      throw new ValidationFailedException("Stake is outside configured bounds");
    }
  }

  private void requireTotalOdds(List<BetLeg> legs) {
    BigDecimal product = BigDecimal.ONE;
    for (BetLeg leg : legs) {
      product = product.multiply(leg.oddsAtSubmission().decimal());
    }
    if (product.compareTo(policy.maxTotalOdds()) > 0) {
      throw new ValidationFailedException("Maximum total odds exceeded");
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
