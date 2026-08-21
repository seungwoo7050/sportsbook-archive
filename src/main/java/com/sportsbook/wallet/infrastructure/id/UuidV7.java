package com.sportsbook.wallet.infrastructure.id;

import java.security.SecureRandom;
import java.util.UUID;

/** Generates RFC 9562 UUID version 7 identifiers with a millisecond-ordered prefix. */
public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final long TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL;
  private static final int TIMESTAMP_SHIFT = 16;
  private static final long VERSION_BITS = 0x7000L;
  private static final int RANDOM_MSB_BOUND = 0x1000;
  private static final long VARIANT_CLEAR_MASK = 0x3FFF_FFFF_FFFF_FFFFL;
  private static final long VARIANT_BITS = 0x8000_0000_0000_0000L;

  public static UUID generate() {
    return generate(System.currentTimeMillis());
  }

  static UUID generate(long unixMillis) {
    long mostSignificantBits = (unixMillis & TIMESTAMP_MASK) << TIMESTAMP_SHIFT;
    mostSignificantBits |= VERSION_BITS;
    mostSignificantBits |= RANDOM.nextInt(RANDOM_MSB_BOUND);

    long leastSignificantBits = RANDOM.nextLong() & VARIANT_CLEAR_MASK;
    leastSignificantBits |= VARIANT_BITS;
    return new UUID(mostSignificantBits, leastSignificantBits);
  }

  private UuidV7() {}
}
