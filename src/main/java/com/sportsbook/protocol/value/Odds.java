package com.sportsbook.protocol.value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Decimal odds with scale-normalized equality. */
public record Odds(BigDecimal decimal) {

  public static final int SCALE = 4;

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
}
