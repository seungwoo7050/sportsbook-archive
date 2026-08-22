package com.sportsbook.betting.domain;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
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

  public Money maxPayout(BetSlipType type, Money unitStake, List<Odds> odds) {
    BigDecimal sum = BigDecimal.ZERO;
    for (List<Integer> line : lines(type, odds.size())) {
      BigDecimal product = BigDecimal.ONE;
      for (int index : line) {
        product = product.multiply(odds.get(index).decimal());
      }
      sum = sum.add(product);
    }
    long amount =
        BigDecimal.valueOf(unitStake.amount())
            .multiply(sum)
            .setScale(0, RoundingMode.FLOOR)
            .longValueExact();
    return new Money(amount, unitStake.currency());
  }

  private static List<List<Integer>> lines(BetSlipType type, int count) {
    if (type instanceof BetSlipType.System system) {
      List<List<Integer>> result = new ArrayList<>();
      collect(0, count, system.minWins(), new ArrayList<>(), result);
      return result;
    }
    return List.of(IntStream.range(0, count).boxed().toList());
  }

  private static void collect(
      int start, int count, int size, List<Integer> current, List<List<Integer>> result) {
    if (current.size() == size) {
      result.add(List.copyOf(current));
      return;
    }
    for (int index = start; index <= count - (size - current.size()); index++) {
      current.add(index);
      collect(index + 1, count, size, current, result);
      current.remove(current.size() - 1);
    }
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
