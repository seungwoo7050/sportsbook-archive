package com.sportsbook.wallet.domain.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BalanceLimitExceededExceptionTest {

  @Test
  void preservesAggregateBalanceFacts() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000002");
    var failure = new BalanceLimitExceededException(userId, 7L, 9L);

    assertThat(failure.userId()).isEqualTo(userId);
    assertThat(failure.availableAmount()).isEqualTo(7L);
    assertThat(failure.lockedAmount()).isEqualTo(9L);
    assertThat(failure)
        .hasMessage("Account " + userId + " balance exceeds Long.MAX_VALUE: available=7, locked=9");
  }
}
