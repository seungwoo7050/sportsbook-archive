package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationKeysTest {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final BetId BET = BetId.of(new UUID(0, 2));
  private static final SelectionId SELECTION = SelectionId.of(new UUID(0, 3));

  @Test
  void separatesCurrencyStakeFromNeutralSelectionCapacity() {
    assertThat(ReservationKeys.activeStakes(USER, Currency.KRW))
        .isNotEqualTo(ReservationKeys.activeStakes(USER, Currency.USD));
    assertThat(ReservationKeys.activeSelections(USER).entries()).doesNotContain("krw", "usd");
    assertThat(ReservationKeys.activeSelection(USER, SELECTION)).doesNotContain("krw", "usd");
    assertThat(ReservationKeys.activeBets(USER)).contains("{" + USER.value() + "}");
  }

  @Test
  void keepsLifecycleAndIngestionIdentityUnambiguous() {
    assertThat(ReservationKeys.lifecycle(BET)).endsWith(BET.value().toString());
    assertThat(ReservationKeys.acceptedFingerprint(BET)).endsWith(BET.value().toString());
    assertThat(ReservationKeys.lifecycle(BET))
        .isNotEqualTo(ReservationKeys.acceptedFingerprint(BET));
    assertThat(ReservationKeys.ACTIVE_COUNT).isEqualTo("risk:reservations:active");
  }
}
