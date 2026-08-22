package com.sportsbook.betting.domain;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import org.springframework.stereotype.Component;

@Component
public class SystemBetCalculator {

  public int lineCount(BetSlipType type, int legCount) {
    if (type instanceof BetSlipType.System system) {
      if (system.totalSelections() != legCount) {
        throw new IllegalArgumentException("SYSTEM totalSelections must equal leg count");
      }
      return Math.toIntExact(binomial(legCount, system.minWins()));
    }
    return 1;
  }

  public Money totalStake(BetSlipType type, Money unitStake, int legCount) {
    return unitStake.multiply(lineCount(type, legCount));
  }

  static long binomial(int n, int k) {
    if (k < 0 || k > n) {
      return 0;
    }
    int smaller = Math.min(k, n - k);
    long result = 1;
    for (int index = 0; index < smaller; index++) {
      result = result * (n - index) / (index + 1);
    }
    return result;
  }
}
