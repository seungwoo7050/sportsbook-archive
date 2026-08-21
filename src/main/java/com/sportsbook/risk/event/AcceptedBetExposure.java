package com.sportsbook.risk.event;

import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.BetSlipTypeTag;
import com.sportsbook.risk.policy.SafeRedisNumber;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.util.Objects;

/** Exact monetary exposure represented by an accepted bet event. */
public record AcceptedBetExposure(long totalAmount) {

  public AcceptedBetExposure {
    SafeRedisNumber.requirePositive(totalAmount, "totalAmount");
  }

  public static AcceptedBetExposure from(BetPlacedRequested event) {
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(event.getSelections(), "selections");
    int selectionCount = event.getSelections().size();
    if (selectionCount < 1 || selectionCount > RiskCheckCommand.MAX_SELECTIONS) {
      throw new IllegalArgumentException("selection count must be between 1 and 15");
    }

    BetSlipTypeTag slipType = Objects.requireNonNull(event.getSlipType(), "slipType");
    long unitAmount =
        SafeRedisNumber.requirePositive(
            Objects.requireNonNull(event.getStake(), "stake").getAmount(), "stake.amount");
    long total =
        switch (slipType) {
          case SINGLE -> unitAmount(selectionCount, event);
          case MULTIPLE -> multipleAmount(selectionCount, unitAmount, event);
          case SYSTEM -> systemAmount(selectionCount, unitAmount, event);
        };
    return new AcceptedBetExposure(total);
  }

  private static long unitAmount(int actual, BetPlacedRequested event) {
    requireNoSystemShape(event);
    if (actual != 1) {
      throw new IllegalArgumentException("SINGLE must contain exactly one selection");
    }
    return event.getStake().getAmount();
  }

  private static long multipleAmount(int count, long unitAmount, BetPlacedRequested event) {
    requireNoSystemShape(event);
    if (count < 2) {
      throw new IllegalArgumentException("MULTIPLE must contain at least two selections");
    }
    return unitAmount;
  }

  private static long systemAmount(int count, long unitAmount, BetPlacedRequested event) {
    Integer minimum = event.getSystemMinWins();
    Integer total = event.getSystemTotalSelections();
    if (minimum == null || total == null) {
      throw new IllegalArgumentException("SYSTEM requires minWins and totalSelections");
    }
    if (total != count || total < 2 || total > RiskCheckCommand.MAX_SELECTIONS) {
      throw new IllegalArgumentException("SYSTEM totalSelections must match 2..15 selections");
    }
    if (minimum < 1 || minimum > total) {
      throw new IllegalArgumentException("SYSTEM minWins must be between 1 and totalSelections");
    }
    return SafeRedisNumber.multiply(unitAmount, combinations(total, minimum), "totalAmount");
  }

  private static void requireNoSystemShape(BetPlacedRequested event) {
    if (event.getSystemMinWins() != null || event.getSystemTotalSelections() != null) {
      throw new IllegalArgumentException("non-SYSTEM slip must not contain SYSTEM fields");
    }
  }

  private static long combinations(int total, int choose) {
    int smaller = Math.min(choose, total - choose);
    long result = 1L;
    for (int index = 1; index <= smaller; index++) {
      result = Math.multiplyExact(result, total - smaller + index) / index;
    }
    return result;
  }
}
