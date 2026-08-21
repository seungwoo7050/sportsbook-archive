package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.error.BalanceLimitExceededException;
import com.sportsbook.wallet.domain.error.CurrencyMismatchException;
import com.sportsbook.wallet.domain.error.InsufficientBalanceException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountAvailableFundsTest {

  private static final Instant OPENED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant CHANGED_AT = Instant.parse("2026-01-01T00:00:01Z");

  private Account account;

  @BeforeEach
  void openAccount() {
    account = Account.openFor(UUID.randomUUID(), Currency.KRW, OPENED_AT);
  }

  @Test
  void depositsAndWithdrawsAvailableFunds() {
    account.increaseAvailable(Money.krw(100L), CHANGED_AT);
    account.decreaseAvailable(Money.krw(40L), CHANGED_AT.plusSeconds(1L));

    assertThat(account.available()).isEqualTo(Money.krw(60L));
    assertThat(account.updatedAt()).isEqualTo(CHANGED_AT.plusSeconds(1L));
  }

  @Test
  void validatesPositiveMoneyBeforeMutation() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> account.increaseAvailable(Money.zero(Currency.KRW), CHANGED_AT));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> account.decreaseAvailable(Money.krw(-1L), CHANGED_AT));

    assertThat(account.available()).isEqualTo(Money.zero(Currency.KRW));
  }

  @Test
  void rejectsCurrencyMismatchAndInsufficientFunds() {
    assertThatThrownBy(() -> account.increaseAvailable(Money.usd(1L), CHANGED_AT))
        .isInstanceOf(CurrencyMismatchException.class);
    assertThatThrownBy(() -> account.decreaseAvailable(Money.krw(1L), CHANGED_AT))
        .isInstanceOf(InsufficientBalanceException.class);
  }

  @Test
  void rejectsAnIncreasePastTheAggregateLimit() {
    account.increaseAvailable(Money.krw(Long.MAX_VALUE), CHANGED_AT);

    assertThatThrownBy(() -> account.increaseAvailable(Money.krw(1L), CHANGED_AT))
        .isInstanceOf(BalanceLimitExceededException.class);
  }
}
