package com.sportsbook.settlement.resolver;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Calculates unit-stake line payouts with one final minor-unit floor. */
public final class SettlementPayoutCalculator {

  public PayoutCalculation calculate(List<SettlementLine> lines, Money unitStake) {
    if (lines == null || lines.isEmpty() || unitStake == null || unitStake.amount() <= 0) {
      throw new IllegalArgumentException("Payout requires lines and a positive unit stake");
    }
    BigDecimal summedProducts = BigDecimal.ZERO;
    int surviving = 0;
    for (SettlementLine line : lines) {
      BigDecimal product = product(line);
      summedProducts = summedProducts.add(product);
      if (product.signum() > 0) {
        surviving++;
      }
    }
    long amount =
        BigDecimal.valueOf(unitStake.amount())
            .multiply(summedProducts)
            .setScale(0, RoundingMode.FLOOR)
            .longValueExact();
    return new PayoutCalculation(new Money(amount, unitStake.currency()), surviving, lines.size());
  }

  private static BigDecimal product(SettlementLine line) {
    BigDecimal product = BigDecimal.ONE;
    for (ResolvedSelection selection : line.selections()) {
      if (selection.outcome() == SettlementResult.LOST) {
        return BigDecimal.ZERO;
      }
      if (selection.outcome() == SettlementResult.WON) {
        product = product.multiply(selection.odds().decimal());
      }
    }
    return product;
  }
}
