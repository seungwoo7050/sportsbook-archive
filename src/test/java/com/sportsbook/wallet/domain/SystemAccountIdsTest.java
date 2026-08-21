package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SystemAccountIdsTest {

  @Test
  void recognizesOnlyStableSystemCounterparties() {
    assertThat(SystemAccountIds.HOUSE)
        .isEqualTo(UUID.fromString("00000000-0000-7000-8000-000000000001"));
    assertThat(SystemAccountIds.EXTERNAL_PAYMENT)
        .isEqualTo(UUID.fromString("00000000-0000-7000-8000-000000000002"));
    assertThat(SystemAccountIds.HOUSE).isNotEqualTo(SystemAccountIds.EXTERNAL_PAYMENT);
    assertThat(SystemAccountIds.isSystemAccount(SystemAccountIds.HOUSE)).isTrue();
    assertThat(SystemAccountIds.isSystemAccount(SystemAccountIds.EXTERNAL_PAYMENT)).isTrue();
    assertThat(
            SystemAccountIds.isSystemAccount(
                UUID.fromString("019b76da-a000-7000-8000-000000000001")))
        .isFalse();
  }
}
