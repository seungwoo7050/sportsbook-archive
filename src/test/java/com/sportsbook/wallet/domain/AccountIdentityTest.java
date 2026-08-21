package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountIdentityTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void opensAnEmptyUserAccount() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000001");

    Account account = Account.openFor(userId, Currency.KRW, NOW);

    assertThat(account.userId()).isEqualTo(userId);
    assertThat(account.available()).isEqualTo(Money.zero(Currency.KRW));
    assertThat(account.locked()).isEqualTo(Money.zero(Currency.KRW));
    assertThat(account.createdAt()).isEqualTo(NOW);
  }

  @Test
  void rejectsReservedSystemCounterparties() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Account.openFor(SystemAccountIds.HOUSE, Currency.KRW, NOW));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Account.openFor(SystemAccountIds.EXTERNAL_PAYMENT, Currency.KRW, NOW));
  }
}
