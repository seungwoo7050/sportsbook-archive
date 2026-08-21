package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.wallet.domain.error.BalanceLimitExceededException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountBalanceLimitTest {

  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000002");

  @Test
  void acceptsEverySplitAtTheAggregateLimit() {
    assertThatCode(() -> Account.requireRepresentableBalance(USER_ID, Long.MAX_VALUE, 0L))
        .doesNotThrowAnyException();
    assertThatCode(() -> Account.requireRepresentableBalance(USER_ID, Long.MAX_VALUE - 1L, 1L))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsAnAggregateOneUnitPastTheLimit() {
    assertThatThrownBy(() -> Account.requireRepresentableBalance(USER_ID, Long.MAX_VALUE, 1L))
        .isInstanceOf(BalanceLimitExceededException.class);
    assertThatThrownBy(() -> Account.requireRepresentableBalance(USER_ID, 1L, Long.MAX_VALUE))
        .isInstanceOf(BalanceLimitExceededException.class);
  }
}
