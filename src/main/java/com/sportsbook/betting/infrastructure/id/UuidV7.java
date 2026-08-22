package com.sportsbook.betting.infrastructure.id;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final long TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL;
  private static final long VERSION_MASK = 0x7000L;
  private static final long VARIANT_MASK = 0x8000_0000_0000_0000L;

  public static UUID generate() {
    return generate(System.currentTimeMillis());
  }

  static UUID generate(long unixMillis) {
    long mostSignificantBits = ((unixMillis & TIMESTAMP_MASK) << 16) | VERSION_MASK;
    mostSignificantBits |= RANDOM.nextInt(0x1000);
    long leastSignificantBits = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | VARIANT_MASK;
    return new UUID(mostSignificantBits, leastSignificantBits);
  }

  private UuidV7() {}
}
