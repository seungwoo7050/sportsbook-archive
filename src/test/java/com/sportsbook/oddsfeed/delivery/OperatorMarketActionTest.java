package com.sportsbook.oddsfeed.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperatorMarketActionTest {

  private static final UUID ACTION_ID = UUID.randomUUID();
  private static final EventId EVENT_ID = new EventId(UUID.randomUUID());
  private static final MarketId MARKET_ID = new MarketId(UUID.randomUUID());
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-21T05:00:00Z");

  @Test
  void carriesTheAcceptedTransition() {
    OperatorMarketAction action = action(2, 1, "incident");

    assertThat(action.actionId()).isEqualTo(ACTION_ID);
    assertThat(action.eventId()).isEqualTo(EVENT_ID);
    assertThat(action.marketId()).isEqualTo(MARKET_ID);
    assertThat(action.previousStatus()).isEqualTo(MarketStatus.OPEN);
    assertThat(action.announcedStatus()).isEqualTo(MarketStatus.SUSPENDED);
    assertThat(action.requestedStatus()).isEqualTo(MarketStatus.SUSPENDED);
    assertThat(action.reason()).isEqualTo("incident");
    assertThat(action.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void rejectsMissingRequiredFields() {
    assertThatThrownBy(() -> action(1, 0, null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void requiresPositiveConsecutiveSequences() {
    assertThatThrownBy(() -> action(0, -1, "incident"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> action(3, 1, "incident")).isInstanceOf(IllegalArgumentException.class);
  }

  private static OperatorMarketAction action(long sequence, long predecessor, String reason) {
    return new OperatorMarketAction(
        ACTION_ID,
        EVENT_ID,
        MARKET_ID,
        MarketStatus.OPEN,
        MarketStatus.SUSPENDED,
        MarketStatus.SUSPENDED,
        reason,
        sequence,
        predecessor,
        OCCURRED_AT);
  }
}
