package com.sportsbook.admin.context;

import java.security.SecureRandom;
import java.util.UUID;

public final class Uuid7 {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TIMESTAMP_SHIFT = 16;
  private static final long TIMESTAMP_MASK = 0xFFFFFFFFFFFFL;
  private static final long VERSION_BITS = 0x7000L;
  private static final int RANDOM_A_BOUND = 0x1000;
  private static final long VARIANT_BITS = 0x8000000000000000L;
  private static final long RANDOM_B_MASK = 0x3FFFFFFFFFFFFFFFL;

  private Uuid7() {}

  public static UUID generate() {
    long timestamp = System.currentTimeMillis() & TIMESTAMP_MASK;
    long randomA = RANDOM.nextInt(RANDOM_A_BOUND);
    long mostSignificant = (timestamp << TIMESTAMP_SHIFT) | VERSION_BITS | randomA;
    long leastSignificant = VARIANT_BITS | (RANDOM.nextLong() & RANDOM_B_MASK);
    return new UUID(mostSignificant, leastSignificant);
  }
}
