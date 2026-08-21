package com.sportsbook.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RiskCheckSelectionTest {
  @Test
  void requiresOneToFifteenUniqueSelections() {
    SelectionId selection = selection(1);

    assertThatThrownBy(() -> command(List.of())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> command(List.of(selection, selection)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unique");
    assertThatThrownBy(
            () ->
                command(
                    IntStream.rangeClosed(1, RiskCheckCommand.MAX_SELECTIONS + 1)
                        .mapToObj(RiskCheckSelectionTest::selection)
                        .toList()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isolatesSelectionsFromCallerMutation() {
    var mutable = new ArrayList<>(List.of(selection(1)));
    RiskCheckCommand command = command(mutable);
    mutable.add(selection(2));

    assertThat(command.selectionIds()).hasSize(1);
    assertThatThrownBy(() -> command.selectionIds().add(selection(3)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static RiskCheckCommand command(List<SelectionId> selections) {
    return new RiskCheckCommand(
        UserId.of(UUID.randomUUID()),
        BetId.of(UUID.randomUUID()),
        Money.krw(100L),
        selections,
        Instant.EPOCH);
  }

  private static SelectionId selection(int suffix) {
    return SelectionId.of(new UUID(0L, suffix));
  }
}
