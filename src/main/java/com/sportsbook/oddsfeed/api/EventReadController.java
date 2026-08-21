package com.sportsbook.oddsfeed.api;

import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.protocol.value.EventId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "size", defaultValue = "20") int requestedSize) {
    int size = clampSize(requestedSize);
    List<EventSummary> events = catalog.orderedByKickoff();
    int startIndex = cursor == null ? 0 : indexAfter(events, decodeCursor(cursor));
    int endIndex = Math.min(startIndex + size, events.size());
    List<EventSummary> page = events.subList(startIndex, endIndex);
    String nextCursor = endIndex < events.size() ? encodeCursor(page.get(page.size() - 1)) : null;
    return new CursorPage<>(page, nextCursor);
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

  static String encodeCursor(EventSummary summary) {
    String value = summary.scheduledStartAt() + "|" + summary.eventId().value();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  static Cursor decodeCursor(String encoded) {
    try {
      String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
      int separator = value.indexOf('|');
      if (separator < 0) {
        throw new IllegalArgumentException("Missing cursor separator");
      }
      return new Cursor(
          Instant.parse(value.substring(0, separator)),
          UUID.fromString(value.substring(separator + 1)));
    } catch (IllegalArgumentException | java.time.format.DateTimeParseException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor", exception);
    }
  }

  private static int indexAfter(List<EventSummary> events, Cursor cursor) {
    for (int index = 0; index < events.size(); index++) {
      EventSummary event = events.get(index);
      int kickoffOrder = event.scheduledStartAt().compareTo(cursor.kickoff());
      if (kickoffOrder > 0
          || kickoffOrder == 0 && event.eventId().value().compareTo(cursor.eventId()) > 0) {
        return index;
      }
    }
    return events.size();
  }

  record Cursor(Instant kickoff, UUID eventId) {}
}
