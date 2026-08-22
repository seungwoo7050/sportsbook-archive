package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionPlanShapeTest {

  @Test
  void rejectsAStoredSlipWithMissingSelections() {
    RevisionPlanRow row =
        new RevisionPlanRow(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.WON,
            200,
            SettlementResult.PUSH,
            100,
            Currency.KRW,
            SlipKind.SYSTEM,
            1,
            2,
            100,
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(1));
    ResolvedSelection only =
        new ResolvedSelection(UUID.randomUUID(), Odds.ofDecimal("2.0000"), SettlementResult.WON);

    assertThatThrownBy(() -> row.toPlan(List.of(only)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("selection shape");
  }
}
