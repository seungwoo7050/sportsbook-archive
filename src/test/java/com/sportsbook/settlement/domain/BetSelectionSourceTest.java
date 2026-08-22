package com.sportsbook.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Odds;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetSelectionSourceTest {

  @Test
  void treatsCandidateIdentityAsPartOfTheResolvedSnapshot() {
    BetSelection selection =
        new BetSelection(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
    UUID first = UUID.randomUUID();
    UUID replacement = UUID.randomUUID();

    assertThat(selection.sourceCandidateId()).isNull();
    assertThat(selection.applyCandidate(first, SettlementResult.WON)).isTrue();
    assertThat(selection.applyCandidate(first, SettlementResult.WON)).isFalse();
    assertThat(selection.applyCandidate(replacement, SettlementResult.WON)).isTrue();
    assertThat(selection.sourceCandidateId()).isEqualTo(replacement);
    assertThat(selection.outcome()).isEqualTo(SettlementResult.WON);
  }
}
