package com.sportsbook.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.policy.SafeRedisNumber;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskCheckCommandTest {
  private static final UserId USER_ID = UserId.of(UUID.randomUUID());
  private static final BetId BET_ID = BetId.of(UUID.randomUUID());
  private static final SelectionId SELECTION_ID = SelectionId.of(UUID.randomUUID());

  @Test
  void retainsTypedCandidateValues() {
    var command = command(new Money(100L, Currency.KRW), new ArrayList<>(List.of(SELECTION_ID)));

    assertThat(command.userId()).isEqualTo(USER_ID);
    assertThat(command.betId()).isEqualTo(BET_ID);
    assertThat(command.selectionIds()).containsExactly(SELECTION_ID);
  }

  @Test
  void rejectsAmountsOutsideTheRedisIntegerDomain() {
    assertThatThrownBy(() -> command(new Money(0L, Currency.KRW), List.of(SELECTION_ID)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                command(
                    new Money(SafeRedisNumber.MAX_VALUE + 1L, Currency.KRW), List.of(SELECTION_ID)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static RiskCheckCommand command(Money stake, List<SelectionId> selections) {
    return new RiskCheckCommand(USER_ID, BET_ID, stake, selections, Instant.EPOCH);
  }
}
