package com.sportsbook.oddsfeed.api;

import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.protocol.value.EventId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Serves current event projections. */
@RestController
@RequestMapping("/api/v1/events")
public class EventReadController {

  private final EventCatalog catalog;

  public EventReadController(EventCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/{eventId}")
  public ResponseEntity<EventSummary> get(@PathVariable("eventId") UUID eventId) {
    return catalog
        .get(new EventId(eventId))
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
  }
}
