package com.sportsbook.oddsfeed.api;

import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.protocol.value.EventId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class EventCatalog {

  private final Map<EventId, EventSummary> events = new ConcurrentHashMap<>();

  public void put(EventSummary summary) {
    events.put(summary.eventId(), summary);
  }

  public boolean putIfAbsent(EventSummary summary) {
    return events.putIfAbsent(summary.eventId(), summary) == null;
  }

  public Optional<EventSummary> get(EventId eventId) {
    return Optional.ofNullable(events.get(eventId));
  }

  public List<EventSummary> orderedByKickoff() {
    return events.values().stream()
        .sorted(
            Comparator.comparing(EventSummary::scheduledStartAt)
                .thenComparing(event -> event.eventId().value()))
        .toList();
  }

  public int size() {
    return events.size();
  }
}
