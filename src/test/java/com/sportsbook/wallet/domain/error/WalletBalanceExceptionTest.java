package com.sportsbook.wallet.domain.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletBalanceExceptionTest {

  @Test
  void preservesCurrencyMismatchFacts() {
    var failure = new CurrencyMismatchException(Currency.KRW, Currency.USD);

    assertThat(failure.expected()).isEqualTo(Currency.KRW);
    assertThat(failure.actual()).isEqualTo(Currency.USD);
    assertThat(failure).hasMessage("Expected currency KRW but received USD");
  }

  @Test
  void identifiesInsufficientBalanceFacts() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000001");
    Money requested = Money.krw(20L);
    Money available = Money.krw(10L);

    assertThat(new InsufficientBalanceException(userId, requested, available))
        .hasMessage(
            "Account "
                + userId
                + " cannot debit "
                + requested
                + " from available balance "
                + available);
  }
}
