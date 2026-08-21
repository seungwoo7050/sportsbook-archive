package com.sportsbook.protocol.value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/** Decimal odds with scale-normalized equality. */
public record Odds(BigDecimal decimal) {

  public static final int SCALE = 4;
  private static final BigDecimal TWO = new BigDecimal("2.0000");
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int MIN_AMERICAN_MAGNITUDE = 100;

  public Odds {
    Objects.requireNonNull(decimal, "decimal");
    if (decimal.compareTo(BigDecimal.ONE) < 0) {
      throw new IllegalArgumentException("Odds must be at least 1.00: " + decimal);
    }
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Odds odds && decimal.compareTo(odds.decimal) == 0;
  }

  @Override
  public int hashCode() {
    return decimal.setScale(SCALE, RoundingMode.HALF_EVEN).hashCode();
  }

  public static Odds ofDecimal(BigDecimal value) {
    return new Odds(value.setScale(SCALE, RoundingMode.HALF_EVEN));
  }

  public static Odds ofDecimal(String value) {
    return ofDecimal(new BigDecimal(value));
  }

  public String toAmerican() {
    BigDecimal net = decimal.subtract(BigDecimal.ONE);
    if (decimal.compareTo(TWO) >= 0) {
      int value = net.multiply(HUNDRED).setScale(0, RoundingMode.HALF_EVEN).intValueExact();
      return "+" + value;
    }
    return "-" + HUNDRED.divide(net, 0, RoundingMode.HALF_EVEN).intValueExact();
  }

  public String toFractional() {
    BigDecimal net = decimal.subtract(BigDecimal.ONE).setScale(SCALE, RoundingMode.HALF_EVEN);
    BigInteger numerator = net.movePointRight(SCALE).toBigInteger();
    BigInteger denominator = BigDecimal.ONE.movePointRight(SCALE).toBigInteger();
    BigInteger divisor = numerator.gcd(denominator);
    return numerator.divide(divisor) + "/" + denominator.divide(divisor);
  }

  public static Odds ofAmerican(int american) {
    if (Math.abs(american) < MIN_AMERICAN_MAGNITUDE) {
      throw new IllegalArgumentException(
          "American odds magnitude must be at least 100: " + american);
    }
    BigDecimal decimal =
        american > 0
            ? BigDecimal.valueOf(american)
                .divide(HUNDRED, SCALE, RoundingMode.HALF_EVEN)
                .add(BigDecimal.ONE)
            : HUNDRED
                .divide(BigDecimal.valueOf(-american), SCALE, RoundingMode.HALF_EVEN)
                .add(BigDecimal.ONE);
    return ofDecimal(decimal);
  }
}
