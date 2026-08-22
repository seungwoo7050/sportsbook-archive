package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaceBetCommandTest {

  @Test
  void defensivelyCopiesSelections() {
    var mutable = new ArrayList<PlaceBetCommand.SelectionInput>();
    PlaceBetCommand command =
        new PlaceBetCommand(
            UUID.randomUUID(),
            new BetSlipType.Single(),
            mutable,
            Money.krw(1_000),
            IdempotencyKey.of("request-1"));

    assertThat(command.selections()).isEmpty();
    assertThatThrownBy(() -> command.selections().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
