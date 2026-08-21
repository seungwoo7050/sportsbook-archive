package com.sportsbook.oddsfeed.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.SelectionId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CacheKeysTest {

  private static final EventId EVENT =
      new EventId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final MarketId MARKET =
      new MarketId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final SelectionId SELECTION =
      new SelectionId(UUID.fromString("00000000-0000-0000-0000-000000000003"));

  @Test
  void createsStableProjectionKeys() {
    assertThat(CacheKeys.odds(EVENT, MARKET, SELECTION))
        .isEqualTo("odds:" + EVENT.value() + ":" + MARKET.value() + ":" + SELECTION.value());
    assertThat(CacheKeys.event(EVENT)).isEqualTo("event:" + EVENT.value());
    assertThat(CacheKeys.market(EVENT, MARKET))
        .isEqualTo("market:" + EVENT.value() + ":" + MARKET.value());
    assertThat(CacheKeys.providerMarket(EVENT, MARKET))
        .isEqualTo("market:provider:" + EVENT.value() + ":" + MARKET.value());
    assertThat(CacheKeys.marketOverride(EVENT, MARKET))
        .isEqualTo("market:override:" + EVENT.value() + ":" + MARKET.value());
    assertThat(CacheKeys.eventMarkets(EVENT)).isEqualTo("event:markets:" + EVENT.value());
    assertThat(CacheKeys.eventTerminal(EVENT)).isEqualTo("event:terminal:" + EVENT.value());
    assertThat(CacheKeys.marketTerminal(EVENT, MARKET))
        .isEqualTo("market:terminal:" + EVENT.value() + ":" + MARKET.value());
    assertThat(CacheKeys.marketFeedHold(EVENT, MARKET))
        .isEqualTo("market:feed-hold:" + EVENT.value() + ":" + MARKET.value());
  }
}
