package com.sportsbook.wallet.service.command;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementCommandTest {

  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000123");
  private static final IdempotencyKey KEY = IdempotencyKey.of("settlement:validation");

  @Test
  void rejectsNonPositiveCreditsAndForfeits() {
    for (Money invalid : new Money[] {Money.zero(Currency.KRW), new Money(-1L, Currency.KRW)}) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  new CreditCommand(
                      USER_ID, invalid, CreditCommand.Source.HOUSE_POOL, CreditReason.PAYOUT, KEY));
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new ForfeitCommand(USER_ID, invalid, KEY));
    }

    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CreditCommand(
                    USER_ID, Money.krw(1L), CreditCommand.Source.HOUSE_POOL, null, KEY));
  }
}
