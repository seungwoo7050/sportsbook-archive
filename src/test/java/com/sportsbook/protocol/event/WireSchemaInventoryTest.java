package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class WireSchemaInventoryTest {

  private static final String NAMESPACE = "com.sportsbook.protocol.event.";

  @Test
  void wireV1ContainsExactRecordSet() throws Exception {
    assertThat(WireSchemaTestSupport.loadSchemas().keySet())
        .containsExactlyInAnyOrderElementsOf(
            Set.of(
                NAMESPACE + "BetPlacedRequested",
                NAMESPACE + "BetResolutionRevised",
                NAMESPACE + "BetSettled",
                NAMESPACE + "BetVoided",
                NAMESPACE + "EventLifecycle",
                NAMESPACE + "MarketStatusChanged",
                NAMESPACE + "MatchResult",
                NAMESPACE + "Money",
                NAMESPACE + "OddsChanged",
                NAMESPACE + "RiskLimitViolated",
                NAMESPACE + "RiskPatternSuspected",
                NAMESPACE + "WalletCredited",
                NAMESPACE + "WalletDebitFailed",
                NAMESPACE + "WalletDebited"));
  }
}
