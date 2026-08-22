package com.sportsbook.betting.api;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.error.ValidationFailedException;
import com.sportsbook.betting.placement.BetPlacementService;
import com.sportsbook.betting.placement.BetQueryService;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.value.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/bets")
public class BetController {

  private final BetPlacementService placement;
  private final BetQueryService queries;

  public BetController(BetPlacementService placement, BetQueryService queries) {
    this.placement = placement;
    this.queries = queries;
  }

  @PostMapping
  ResponseEntity<BetResponse> place(
      HttpServletRequest request, @Valid @RequestBody PlaceBetRequest body) {
    UUID actor = actor(request);
    Bet bet =
        placement.place(
            body.toCommand(actor, IdempotencyKey.of(single(request, "Idempotency-Key"))));
    HttpStatus status =
        bet.status() == BetStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
    URI location = URI.create("/api/v1/bets/" + bet.betId());
    return ResponseEntity.status(status).location(location).body(BetResponse.from(bet));
  }

  @GetMapping
  CursorPage<BetResponse> page(
      HttpServletRequest request,
      @RequestParam(required = false) UUID cursor,
      @RequestParam(required = false) Integer limit) {
    CursorPage<Bet> page = queries.page(actor(request), cursor, limit);
    return new CursorPage<>(
        page.items().stream().map(BetResponse::from).toList(), page.nextCursor(), page.hasMore());
  }

  @GetMapping("/{betId}")
  BetResponse byId(HttpServletRequest request, @PathVariable UUID betId) {
    return BetResponse.from(queries.byId(actor(request), betId));
  }

  private static UUID actor(HttpServletRequest request) {
    String raw = single(request, "X-User-Id");
    UUID actor = UUID.fromString(raw);
    if (!actor.toString().equals(raw)) {
      throw new ValidationFailedException("X-User-Id must be a canonical lowercase UUID");
    }
    return actor;
  }

  private static String single(HttpServletRequest request, String name) {
    List<String> values = Collections.list(request.getHeaders(name));
    if (values.size() != 1 || values.get(0).isBlank()) {
      throw new ValidationFailedException("Exactly one " + name + " is required");
    }
    return values.get(0);
  }
}
