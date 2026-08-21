package com.sportsbook.oddsfeed.api;

import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.protocol.value.EventId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Serves current event projections. */
@RestController
@RequestMapping("/api/v1/events")
public class EventReadController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final EventCatalog catalog;

  public EventReadController(EventCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public CursorPage<EventSummary> list(
      @RequestParam(value = "size", defaultValue = "20") int requestedSize) {
    int size = clampSize(requestedSize);
    List<EventSummary> events = catalog.orderedByKickoff();
    int endIndex = Math.min(size, events.size());
    return new CursorPage<>(events.subList(0, endIndex), null);
  }

  @GetMapping("/{eventId}")
  public ResponseEntity<EventSummary> get(@PathVariable("eventId") UUID eventId) {
    return catalog
        .get(new EventId(eventId))
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
  }

  static int clampSize(int requestedSize) {
    if (requestedSize <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requestedSize, MAX_PAGE_SIZE);
  }
}
