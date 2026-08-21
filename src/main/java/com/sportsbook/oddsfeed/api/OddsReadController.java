package com.sportsbook.oddsfeed.api;

import com.sportsbook.oddsfeed.cache.RedisOddsCache;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Serves the current cached price for a selection. */
@RestController
@RequestMapping("/api/v1/odds")
public class OddsReadController {

  private final RedisOddsCache cache;

  public OddsReadController(RedisOddsCache cache) {
    this.cache = cache;
  }

  @GetMapping("/{eventId}/{marketId}/{selectionId}")
  public OddsResponse getOdds(
      @PathVariable("eventId") UUID eventId,
      @PathVariable("marketId") UUID marketId,
      @PathVariable("selectionId") UUID selectionId) {
    Odds odds =
        cache
            .getOdds(new EventId(eventId), new MarketId(marketId), new SelectionId(selectionId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Odds not found"));
    return new OddsResponse(eventId, marketId, selectionId, odds.decimal());
  }

  public record OddsResponse(UUID eventId, UUID marketId, UUID selectionId, BigDecimal odds) {}
}
