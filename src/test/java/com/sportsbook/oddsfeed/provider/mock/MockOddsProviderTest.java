package com.sportsbook.oddsfeed.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.MarketType;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockOddsProviderTest {

  @Test
  void marketStartsOpenWithNamedSelections() {
    SelectionId selectionId = new SelectionId(UUID.randomUUID());
    MockOddsProvider.MockSelection selection =
        new MockOddsProvider.MockSelection(selectionId, "HOME", 0.45, Odds.ofDecimal("2.2222"));
    Map<SelectionId, MockOddsProvider.MockSelection> selections = new LinkedHashMap<>();
    selections.put(selectionId, selection);

    MockOddsProvider.MockMarket market =
        new MockOddsProvider.MockMarket(
            new MarketId(UUID.randomUUID()), MarketType.MATCH_RESULT_1X2, selections);

    assertThat(market.status).isEqualTo(MarketStatus.OPEN);
    assertThat(market.type).isEqualTo(MarketType.MATCH_RESULT_1X2);
    assertThat(market.selections.get(selectionId).name).isEqualTo("HOME");
  }
}
