package com.sportsbook.oddsfeed.api;

import com.sportsbook.oddsfeed.delivery.OperatorActionQueue;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.MarketId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Durably accepts authenticated operator market controls. */
@RestController
@RequestMapping("/internal/v1/events/{eventId}/markets/{marketId}")
public class MarketAdminController {

  public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
  public static final String ACTION_ID_HEADER = "X-Admin-Action-Id";

  private final OperatorActionQueue queue;
  private final Clock clock;

  public MarketAdminController(OperatorActionQueue queue, Clock clock) {
    this.queue = queue;
    this.clock = clock;
  }

  @PostMapping("/{action:suspend|close|reopen}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void transition(
      @PathVariable("eventId") UUID eventUuid,
      @PathVariable("marketId") UUID marketUuid,
      @PathVariable("action") String action,
      @RequestHeader(IDEMPOTENCY_HEADER) String rawIdempotencyKey,
      @RequestHeader(ACTION_ID_HEADER) UUID actionId,
      @Valid @RequestBody MarketStatusChangeRequest body) {
    IdempotencyKey idempotencyKey;
    try {
      idempotencyKey = IdempotencyKey.of(rawIdempotencyKey);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key", exception);
    }
    queue.submit(
        idempotencyKey,
        actionId,
        new EventId(eventUuid),
        new MarketId(marketUuid),
        requestedStatus(action),
        body.reason(),
        clock.instant());
  }

  private static MarketStatus requestedStatus(String action) {
    return switch (action) {
      case "suspend" -> MarketStatus.SUSPENDED;
      case "close" -> MarketStatus.CLOSED;
      case "reopen" -> MarketStatus.OPEN;
      default -> throw new IllegalArgumentException("Unsupported operator action");
    };
  }

  public record MarketStatusChangeRequest(@NotBlank @Size(max = 256) String reason) {

    public MarketStatusChangeRequest {
      if (reason != null) {
        reason = reason.trim();
      }
    }
  }
}
