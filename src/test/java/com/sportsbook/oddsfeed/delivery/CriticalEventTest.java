package com.sportsbook.oddsfeed.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CriticalEventTest {

  private final EventId eventId = new EventId(UUID.randomUUID());
  private final MarketId marketId = new MarketId(UUID.randomUUID());

  @Test
  void capturesMarketTransitionValues() {
    Instant occurredAt = Instant.parse("2026-06-01T18:00:00Z");

    CriticalEvent event =
        CriticalEvent.marketStatus(
            eventId,
            marketId,
            MarketStatus.OPEN,
            MarketStatus.SUSPENDED,
            "feed unavailable",
            occurredAt);

    assertThat(event.type()).isEqualTo(CriticalEvent.Type.MARKET_STATUS);
    assertThat(event.eventId()).isEqualTo(eventId.value());
    assertThat(event.marketId()).isEqualTo(marketId.value());
    assertThat(event.previousMarketStatus()).isEqualTo(MarketStatus.OPEN);
    assertThat(event.nextMarketStatus()).isEqualTo(MarketStatus.SUSPENDED);
    assertThat(event.reason()).isEqualTo("feed unavailable");
    assertThat(event.occurredAt()).isEqualTo(occurredAt);
    assertThat(event.lifecycleStatus()).isNull();
  }

  @Test
  void capturesLifecycleValues() {
    Instant kickoff = Instant.parse("2026-06-01T18:00:00Z");
    Instant occurredAt = kickoff.plusSeconds(60);

    CriticalEvent event =
        CriticalEvent.lifecycle(eventId, EventLifecycleStatus.IN_PLAY, kickoff, occurredAt);

    assertThat(event.type()).isEqualTo(CriticalEvent.Type.EVENT_LIFECYCLE);
    assertThat(event.eventId()).isEqualTo(eventId.value());
    assertThat(event.lifecycleStatus()).isEqualTo(EventLifecycleStatus.IN_PLAY);
    assertThat(event.scheduledStartAt()).isEqualTo(kickoff);
    assertThat(event.occurredAt()).isEqualTo(occurredAt);
    assertThat(event.marketId()).isNull();
  }
}
