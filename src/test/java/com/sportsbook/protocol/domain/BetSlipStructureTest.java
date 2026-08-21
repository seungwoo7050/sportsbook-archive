package com.sportsbook.protocol.domain;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetSlipStructureTest {

  @Test
  void slipTypesAcceptTheirRequiredSelectionCounts() {
    pending(new BetSlipType.Single(), List.of(selection()));
    pending(new BetSlipType.Multiple(), List.of(selection(), selection()));
    pending(new BetSlipType.System(2, 3), List.of(selection(), selection(), selection()));
  }

  @Test
  void emptyAndMismatchedSelectionsAreRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> pending(new BetSlipType.Single(), List.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> pending(new BetSlipType.Single(), List.of(selection(), selection())));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> pending(new BetSlipType.Multiple(), List.of(selection())));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> pending(new BetSlipType.System(2, 3), List.of(selection(), selection())));
  }

  @Test
  void stakeMustBePositive() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new BetSlip(
                    BetId.of(UUID.randomUUID()),
                    UserId.of(UUID.randomUUID()),
                    new BetSlipType.Single(),
                    List.of(selection()),
                    Money.krw(0),
                    BetStatus.PENDING,
                    Instant.EPOCH,
                    null,
                    null,
                    null));
  }

  private BetSlip pending(BetSlipType type, List<BetSelection> selections) {
    return new BetSlip(
        BetId.of(UUID.randomUUID()),
        UserId.of(UUID.randomUUID()),
        type,
        selections,
        Money.krw(10_000),
        BetStatus.PENDING,
        Instant.EPOCH,
        null,
        null,
        null);
  }

  private BetSelection selection() {
    return new BetSelection(
        EventId.of(UUID.randomUUID()),
        MarketId.of(UUID.randomUUID()),
        MarketType.MATCH_RESULT_1X2,
        SelectionId.of(UUID.randomUUID()),
        Odds.ofDecimal("1.85"));
  }
}
