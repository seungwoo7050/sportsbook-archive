package com.sportsbook.settlement.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import org.junit.jupiter.api.Test;

class MatchOutcomeModeTest {

  @Test
  void completedRequiresEveryReportedSelectionOutcome() {
    assertThat(MatchOutcomeMode.COMPLETED.resolve(SettlementResult.WON))
        .contains(SettlementResult.WON);
    assertThat(MatchOutcomeMode.COMPLETED.resolve(null)).isEmpty();
  }

  @Test
  void abandonedVoidsOnlyUnreportedSelections() {
    assertThat(MatchOutcomeMode.ABANDONED.resolve(SettlementResult.LOST))
        .contains(SettlementResult.LOST);
    assertThat(MatchOutcomeMode.ABANDONED.resolve(null)).contains(SettlementResult.VOID);
  }

  @Test
  void voidedFinalStatusVoidsEverySelectionThroughNormalResultPath() {
    assertThat(MatchOutcomeMode.VOIDED.resolve(SettlementResult.WON))
        .contains(SettlementResult.VOID);
    assertThat(MatchOutcomeMode.VOIDED.resolve(null)).contains(SettlementResult.VOID);
  }
}
