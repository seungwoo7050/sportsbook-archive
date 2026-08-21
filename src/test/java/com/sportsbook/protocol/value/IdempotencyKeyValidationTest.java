package com.sportsbook.protocol.value;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class IdempotencyKeyValidationTest {

  @Test
  void nullAndBlankKeysAreRejected() {
    assertThatNullPointerException().isThrownBy(() -> IdempotencyKey.of(null));
    assertThatIllegalArgumentException().isThrownBy(() -> IdempotencyKey.of(""));
    assertThatIllegalArgumentException().isThrownBy(() -> IdempotencyKey.of("   "));
  }

  @Test
  void oversizedKeysAreRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IdempotencyKey.of("a".repeat(IdempotencyKey.MAX_LENGTH + 1)))
        .withMessageContaining("exceeds max");
  }

  @Test
  void nonAsciiKeysAreRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IdempotencyKey.of("요청"))
        .withMessageContaining("printable ASCII");
  }

  @Test
  void controlCharactersAreRejected() {
    assertThatIllegalArgumentException().isThrownBy(() -> IdempotencyKey.of("line\nbreak"));
    assertThatIllegalArgumentException().isThrownBy(() -> IdempotencyKey.of("tab\tkey"));
  }
}
