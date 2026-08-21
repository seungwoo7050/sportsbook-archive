package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonicalRequestEncoderTest {

  @Test
  void locksVersionedTlvEncoding() {
    byte[] encoded =
        new CanonicalRequestEncoder()
            .text(1, "KRW")
            .uuid(2, UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"))
            .number(3, 0x0102030405060708L)
            .toByteArray();

    assertThat(HexFormat.of().formatHex(encoded))
        .isEqualTo(
            "73706f727473626f6f6b2e77616c6c65742e6f7065726174696f6e0001"
                + "01000000034b5257"
                + "020000001000112233445566778899aabbccddeeff"
                + "03000000080102030405060708");
  }
}
