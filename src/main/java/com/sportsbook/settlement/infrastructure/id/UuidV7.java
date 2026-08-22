package com.sportsbook.settlement.infrastructure.id;

import java.security.SecureRandom;
import java.util.UUID;

/** RFC 9562 UUID version 7 identifiers for durable settlement records. */
public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final long TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL;
  private static final long VERSION_MASK = 0x7000L;
  private static final long VARIANT_SET_MASK = 0x8000_0000_0000_0000L;
  private static final long VARIANT_CLEAR_MASK = 0x3FFF_FFFF_FFFF_FFFFL;

  private UuidV7() {}

  public static UUID generate() {
    return generate(System.currentTimeMillis());
  }

  static UUID generate(long unixMillis) {
    long mostSignificant = (unixMillis & TIMESTAMP_MASK) << 16;
    mostSignificant |= VERSION_MASK;
    mostSignificant |= RANDOM.nextInt(0x1000);
    long leastSignificant = RANDOM.nextLong() & VARIANT_CLEAR_MASK;
    leastSignificant |= VARIANT_SET_MASK;
    return new UUID(mostSignificant, leastSignificant);
  }
}
