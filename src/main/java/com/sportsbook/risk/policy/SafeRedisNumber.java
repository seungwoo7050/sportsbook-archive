package com.sportsbook.risk.policy;

/** Integer operations that remain exact when Redis Lua represents numbers as doubles. */
public final class SafeRedisNumber {
  public static final long MAX_VALUE = 9_007_199_254_740_991L;

  private SafeRedisNumber() {}

  public static long requireNonNegative(long value, String name) {
    if (value < 0L || value > MAX_VALUE) {
      throw new IllegalArgumentException(name + " must be between 0 and " + MAX_VALUE);
    }
    return value;
  }

  public static long requirePositive(long value, String name) {
    if (value == 0L) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return requireNonNegative(value, name);
  }

  public static long add(long left, long right, String name) {
    requireNonNegative(left, name);
    requireNonNegative(right, name);
    long result = Math.addExact(left, right);
    return requireNonNegative(result, name);
  }

  public static long multiply(long value, long multiplier, String name) {
    requireNonNegative(value, name);
    requireNonNegative(multiplier, name);
    long result = Math.multiplyExact(value, multiplier);
    return requireNonNegative(result, name);
  }
}
