package com.sportsbook.risk.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LimitKeysTest {
  private static final UserId USER =
      UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final BetId BET =
      BetId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));

  @Test
  void isolatesMonetaryWindowsByCurrency() {
    LimitKeys.Keys krw = LimitKeys.monetary(USER, LimitType.STAKE_DAILY, Currency.KRW);
    LimitKeys.Keys usd = LimitKeys.monetary(USER, LimitType.STAKE_DAILY, Currency.USD);

    assertThat(krw.entries()).contains("{00000000-0000-0000-0000-000000000001}");
    assertThat(krw.entries()).endsWith(":stake-daily:krw:entries");
    assertThat(usd.entries()).endsWith(":stake-daily:usd:entries");
    assertThat(krw).isNotEqualTo(usd);
  }

  @Test
  void keepsSelectionCapacityCurrencyNeutral() {
    LimitKeys.Keys selections = LimitKeys.selections(USER);

    assertThat(selections.entries()).endsWith(":selections-per-minute:entries");
    assertThat(selections.sum()).endsWith(":selections-per-minute:sum");
    assertThatThrownBy(
            () -> LimitKeys.monetary(USER, LimitType.SELECTIONS_PER_MINUTE, Currency.KRW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void encodesUuidMembersWithoutAmbiguousIdentityDelimiters() {
    assertThat(LimitKeys.member(BET, 1500)).isEqualTo("00000000-0000-0000-0000-000000000002|1500");
    assertThatThrownBy(() -> LimitKeys.member(BET, 0)).isInstanceOf(IllegalArgumentException.class);
  }
}
