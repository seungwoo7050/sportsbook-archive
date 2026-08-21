package com.sportsbook.wallet.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.SystemAccountIds;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdjustmentCommandTest {
  private static final UUID REVISION_ID = UUID.fromString("019b76da-a000-7000-8000-000000000114");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-000000000115");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000116");
  private static final IdempotencyKey KEY = IdempotencyKey.of("settlement:revision:" + REVISION_ID);

  @Test
  void exposesSignedAndAbsolutePayoutDelta() {
    AdjustmentCommand increase = command(Money.krw(700L), Money.krw(1_000L));
    AdjustmentCommand decrease = command(Money.krw(1_000L), Money.krw(700L));

    assertThat(increase.deltaAmount()).isEqualTo(300L);
    assertThat(increase.absoluteDelta()).isEqualTo(Money.krw(300L));
    assertThat(decrease.deltaAmount()).isEqualTo(-300L);
    assertThat(decrease.absoluteDelta()).isEqualTo(Money.krw(300L));
  }

  @Test
  void rejectsMalformedRevisionIdentity() {
    assertThatThrownBy(
            () ->
                new AdjustmentCommand(
                    REVISION_ID,
                    BET_ID,
                    1L,
                    USER_ID,
                    Money.krw(1L),
                    Money.krw(2L),
                    IdempotencyKey.of("settlement:revision:" + BET_ID)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Idempotency key must identify the revision");
    assertThatThrownBy(
            () ->
                new AdjustmentCommand(
                    REVISION_ID, BET_ID, 0L, USER_ID, Money.krw(1L), Money.krw(2L), KEY))
        .hasMessage("Revision number must be at least one");
  }

  @Test
  void rejectsNegativeEqualAndMixedCurrencySnapshots() {
    assertThatThrownBy(() -> command(new Money(-1L, Currency.KRW), Money.krw(2L)))
        .hasMessage("Payout snapshots cannot be negative");
    assertThatThrownBy(() -> command(Money.krw(2L), Money.krw(2L)))
        .hasMessage("Adjustment delta cannot be zero");
    assertThatThrownBy(() -> command(Money.krw(1L), Money.usd(2L)))
        .hasMessage("Payout snapshot currencies must match");
  }

  @Test
  void rejectsReservedSystemAccountsBeforeClaimingTheKey() {
    assertThatThrownBy(() -> commandFor(SystemAccountIds.HOUSE))
        .hasMessage("System UUID cannot receive an adjustment");
    assertThatThrownBy(() -> commandFor(SystemAccountIds.EXTERNAL_PAYMENT))
        .hasMessage("System UUID cannot receive an adjustment");
  }

  private AdjustmentCommand command(Money previous, Money next) {
    return new AdjustmentCommand(REVISION_ID, BET_ID, 1L, USER_ID, previous, next, KEY);
  }

  private AdjustmentCommand commandFor(UUID userId) {
    return new AdjustmentCommand(
        REVISION_ID, BET_ID, 1L, userId, Money.krw(1L), Money.krw(2L), KEY);
  }
}
