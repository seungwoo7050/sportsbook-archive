package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AccountRecoveryTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000109");
  private static final Instant OPENED = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void allocatesMonotonicSequencesAndKeepsTheFirstFreezeTime() {
    Account account = Account.openFor(USER_ID, Currency.KRW, OPENED);
    Instant first = OPENED.plusSeconds(1);
    Instant second = OPENED.plusSeconds(2);

    assertThat(account.queueRecoveryDebt(Money.krw(Long.MAX_VALUE), first)).isEqualTo(1L);
    assertThat(account.queueRecoveryDebt(Money.krw(1L), second)).isEqualTo(2L);

    assertThat(account.recoveryDebtAmount())
        .isEqualTo(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
    assertThat(account.recoveryFrozenAt()).isEqualTo(first);
    assertThat(account.nextAdjustmentSequence()).isEqualTo(3L);
    assertThat(account.isOutboundFrozen()).isTrue();
  }

  @Test
  void clearsTheFreezeOnlyAfterEveryDebtUnitIsSettled() {
    Account account = Account.openFor(USER_ID, Currency.KRW, OPENED);
    account.increaseAvailable(Money.krw(10L), OPENED.plusSeconds(1));
    account.queueRecoveryDebt(Money.krw(4L), OPENED.plusSeconds(2));
    account.queueRecoveryDebt(Money.krw(6L), OPENED.plusSeconds(3));

    account.recoverAvailable(Money.krw(4L), OPENED.plusSeconds(4));
    assertThat(account.recoveryDebtAmount()).isEqualTo(BigInteger.valueOf(6L));
    assertThat(account.available()).isEqualTo(Money.krw(6L));
    assertThat(account.isOutboundFrozen()).isTrue();

    account.recoverAvailable(Money.krw(6L), OPENED.plusSeconds(5));
    assertThat(account.recoveryDebtAmount()).isZero();
    assertThat(account.recoveryFrozenAt()).isNull();
    assertThat(account.isOutboundFrozen()).isFalse();
  }

  @Test
  void refusesToSettleMoreThanTheOutstandingDebt() {
    Account account = Account.openFor(USER_ID, Currency.KRW, OPENED);
    account.increaseAvailable(Money.krw(4L), OPENED.plusSeconds(1));
    account.queueRecoveryDebt(Money.krw(3L), OPENED.plusSeconds(1));

    assertThatThrownBy(() -> account.recoverAvailable(Money.krw(4L), OPENED.plusSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Recovery payment exceeds outstanding debt");
    assertThat(account.recoveryDebtAmount()).isEqualTo(BigInteger.valueOf(3L));
  }

  @Test
  void refusesSequenceOverflowBeforeChangingRecoveryDebt() {
    Account account = Account.openFor(USER_ID, Currency.KRW, OPENED);
    ReflectionTestUtils.setField(account, "nextAdjustmentSequence", Long.MAX_VALUE);

    assertThatThrownBy(() -> account.queueRecoveryDebt(Money.krw(1L), OPENED.plusSeconds(1)))
        .isInstanceOf(ArithmeticException.class);
    assertThat(account.recoveryDebtAmount()).isZero();
    assertThat(account.nextAdjustmentSequence()).isEqualTo(Long.MAX_VALUE);
  }
}
