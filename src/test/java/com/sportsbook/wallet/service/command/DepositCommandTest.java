package com.sportsbook.wallet.service.command;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DepositCommandTest {

  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000121");
  private static final IdempotencyKey KEY = IdempotencyKey.of("deposit:validation");

  @Test
  void rejectsZeroAndNegativeAmounts() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new DepositCommand(USER_ID, Money.zero(Currency.KRW), KEY));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new DepositCommand(USER_ID, new Money(-1L, Currency.KRW), KEY));
  }
}
