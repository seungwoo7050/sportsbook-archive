package com.sportsbook.wallet.service.command;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboundCommandTest {

  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000122");
  private static final IdempotencyKey KEY = IdempotencyKey.of("outbound:validation");

  @Test
  void rejectsNonPositiveWithdrawalsAndBetDebits() {
    for (Money invalid : new Money[] {Money.zero(Currency.KRW), new Money(-1L, Currency.KRW)}) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new WithdrawCommand(USER_ID, invalid, KEY));
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new DebitCommand(USER_ID, invalid, KEY));
    }
  }
}
