package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.error.CurrencyMismatchException;
import com.sportsbook.wallet.domain.error.InsufficientBalanceException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountLockedFundsTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private Account account;

  @BeforeEach
  void fundAccount() {
    account = Account.openFor(UUID.randomUUID(), Currency.KRW, NOW);
    account.increaseAvailable(Money.krw(100L), NOW);
  }

  @Test
  void stagesAndRefundsLockedFundsWithoutChangingTheTotal() {
    account.moveAvailableToLocked(Money.krw(60L), NOW.plusSeconds(1L));
    account.moveLockedToAvailable(Money.krw(20L), NOW.plusSeconds(2L));

    assertThat(account.available()).isEqualTo(Money.krw(60L));
    assertThat(account.locked()).isEqualTo(Money.krw(40L));
    assertThat(account.total()).isEqualTo(Money.krw(100L));
  }

  @Test
  void forfeitsOnlyTheLockedBucket() {
    account.moveAvailableToLocked(Money.krw(80L), NOW);
    account.forfeitLocked(Money.krw(30L), NOW.plusSeconds(1L));

    assertThat(account.available()).isEqualTo(Money.krw(20L));
    assertThat(account.locked()).isEqualTo(Money.krw(50L));
    assertThat(account.total()).isEqualTo(Money.krw(70L));
  }

  @Test
  void rejectsMissingLockedFundsAndCurrencyMismatch() {
    assertThatThrownBy(() -> account.moveLockedToAvailable(Money.krw(1L), NOW))
        .isInstanceOf(InsufficientBalanceException.class);
    assertThatThrownBy(() -> account.forfeitLocked(Money.usd(1L), NOW))
        .isInstanceOf(CurrencyMismatchException.class);

    assertThat(account.total()).isEqualTo(Money.krw(100L));
  }
}
