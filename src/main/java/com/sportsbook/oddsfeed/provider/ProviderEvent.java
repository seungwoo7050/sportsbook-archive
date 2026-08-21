package com.sportsbook.oddsfeed.provider;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.time.Instant;
import java.util.Objects;

public sealed interface ProviderEvent
    permits ProviderEvent.OddsUpdated,
        ProviderEvent.MarketStatusUpdated,
        ProviderEvent.LifecycleUpdated {

  EventId eventId();

  Instant occurredAt();

  record OddsUpdated(
      EventId eventId,
      MarketId marketId,
      SelectionId selectionId,
      Odds previousOdds,
      Odds newOdds,
      Instant occurredAt)
      implements ProviderEvent {
    public OddsUpdated {
      Objects.requireNonNull(eventId, "eventId");
      Objects.requireNonNull(marketId, "marketId");
      Objects.requireNonNull(selectionId, "selectionId");
      Objects.requireNonNull(previousOdds, "previousOdds");
      Objects.requireNonNull(newOdds, "newOdds");
      Objects.requireNonNull(occurredAt, "occurredAt");
    }
  }

  record MarketStatusUpdated(
      EventId eventId,
      MarketId marketId,
      MarketStatus previousStatus,
      MarketStatus newStatus,
      String reason,
      Instant occurredAt)
      implements ProviderEvent {
    public MarketStatusUpdated {
      Objects.requireNonNull(eventId, "eventId");
      Objects.requireNonNull(marketId, "marketId");
      Objects.requireNonNull(previousStatus, "previousStatus");
      Objects.requireNonNull(newStatus, "newStatus");
      Objects.requireNonNull(occurredAt, "occurredAt");
    }
  }

  record LifecycleUpdated(
      EventId eventId, EventLifecycleStatus status, Instant scheduledStartAt, Instant occurredAt)
      implements ProviderEvent {
    public LifecycleUpdated {
      Objects.requireNonNull(eventId, "eventId");
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(scheduledStartAt, "scheduledStartAt");
      Objects.requireNonNull(occurredAt, "occurredAt");
    }
  }
}
