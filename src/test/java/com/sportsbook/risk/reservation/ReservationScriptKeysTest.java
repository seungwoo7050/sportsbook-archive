package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitKeys;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.limit.LimitOverrideKeys;
import com.sportsbook.risk.pattern.HistoryKeys;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationScriptKeysTest {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final BetId BET = BetId.of(new UUID(0, 2));
  private static final SelectionId FIRST = SelectionId.of(new UUID(0, 3));
  private static final SelectionId SECOND = SelectionId.of(new UUID(0, 4));

  @Test
  void ordersCurrencyAndSelectionFootprints() {
    List<String> keys =
        ReservationScriptKeys.from(
            new RiskCheckCommand(USER, BET, Money.krw(100), List.of(FIRST, SECOND), Instant.EPOCH));

    assertThat(keys)
        .startsWith(
            ReservationKeys.lifecycle(BET),
            ReservationKeys.activeBets(USER),
            ReservationKeys.activeStakes(USER, Money.krw(1).currency()).entries(),
            ReservationKeys.activeStakes(USER, Money.krw(1).currency()).sum(),
            ReservationKeys.activeSelections(USER).entries(),
            ReservationKeys.activeSelections(USER).sum(),
            LimitOverrideKeys.user(USER),
            LimitKeys.monetary(USER, LimitType.STAKE_DAILY, Money.krw(1).currency()).entries())
        .endsWith(
            HistoryKeys.selection(USER, FIRST),
            HistoryKeys.selection(USER, SECOND),
            ReservationKeys.activeSelection(USER, FIRST),
            ReservationKeys.activeSelection(USER, SECOND));
    assertThat(keys).hasSize(22).doesNotHaveDuplicates();
  }
}
