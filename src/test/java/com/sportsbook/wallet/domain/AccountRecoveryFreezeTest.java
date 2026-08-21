package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.error.AccountRecoveryBlockedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountRecoveryFreezeTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000112");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void rejectsOutboundAuthorizationWhileRecoveryDebtExists() {
    Account account = Account.openFor(USER_ID, Currency.KRW, NOW);
    account.queueRecoveryDebt(Money.krw(20L), NOW.plusSeconds(1));

    assertThatThrownBy(account::requireOutboundAllowed)
        .isInstanceOf(AccountRecoveryBlockedException.class)
        .extracting("userId")
        .isEqualTo(USER_ID);
    assertThatThrownBy(() -> account.decreaseAvailable(Money.krw(1L), NOW.plusSeconds(2)))
        .isInstanceOf(AccountRecoveryBlockedException.class);
    assertThatThrownBy(() -> account.moveAvailableToLocked(Money.krw(1L), NOW.plusSeconds(2)))
        .isInstanceOf(AccountRecoveryBlockedException.class);
  }

  @Test
  void allowsInflowsAndForfeitWhileOutboundIsFrozen() {
    Account account = Account.openFor(USER_ID, Currency.KRW, NOW);
    account.increaseAvailable(Money.krw(100L), NOW.plusSeconds(1));
    account.moveAvailableToLocked(Money.krw(50L), NOW.plusSeconds(2));
    account.queueRecoveryDebt(Money.krw(20L), NOW.plusSeconds(3));

    account.increaseAvailable(Money.krw(10L), NOW.plusSeconds(4));
    account.moveLockedToAvailable(Money.krw(10L), NOW.plusSeconds(5));
    account.forfeitLocked(Money.krw(10L), NOW.plusSeconds(6));

    assertThat(account.available()).isEqualTo(Money.krw(70L));
    assertThat(account.locked()).isEqualTo(Money.krw(30L));
    assertThat(account.isOutboundFrozen()).isTrue();
  }
}
