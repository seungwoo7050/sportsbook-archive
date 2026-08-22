package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Odds;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetLegTest {

  @Test
  void retainsSelectionAndQuotedOdds() {
    UUID eventId = UUID.randomUUID();
    UUID marketId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();

    BetLeg leg = BetLeg.create(eventId, marketId, selectionId, Odds.ofDecimal("2.1250"));

    assertThat(leg.legId()).isNotNull();
    assertThat(leg.eventId()).isEqualTo(eventId);
    assertThat(leg.marketId()).isEqualTo(marketId);
    assertThat(leg.selectionId()).isEqualTo(selectionId);
    assertThat(leg.oddsAtSubmission()).isEqualTo(Odds.ofDecimal("2.125"));
  }
}
