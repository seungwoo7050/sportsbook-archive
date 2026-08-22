package com.sportsbook.settlement.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Odds;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SystemCombinationTest {

  @Test
  void expandsKOfNInStableLexicographicOrder() {
    ResolvedSelection a = selection("00000000-0000-0000-0000-000000000001");
    ResolvedSelection b = selection("00000000-0000-0000-0000-000000000002");
    ResolvedSelection c = selection("00000000-0000-0000-0000-000000000003");
    ResolvedSelection d = selection("00000000-0000-0000-0000-000000000004");

    List<SettlementLine> lines =
        new SettlementLineFactory().lines(new BetSlipType.System(2, 4), List.of(a, b, c, d));

    assertThat(lines).hasSize(6);
    assertThat(lines).extracting(SettlementLine::ordinal).containsExactly(0, 1, 2, 3, 4, 5);
    assertThat(lines)
        .extracting(line -> line.selections().stream().map(ResolvedSelection::selectionId).toList())
        .containsExactly(
            List.of(a.selectionId(), b.selectionId()),
            List.of(a.selectionId(), c.selectionId()),
            List.of(a.selectionId(), d.selectionId()),
            List.of(b.selectionId(), c.selectionId()),
            List.of(b.selectionId(), d.selectionId()),
            List.of(c.selectionId(), d.selectionId()));
  }

  private static ResolvedSelection selection(String id) {
    return new ResolvedSelection(
        UUID.fromString(id), Odds.ofDecimal("2.0000"), SettlementResult.WON);
  }
}
