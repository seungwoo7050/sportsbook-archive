package com.sportsbook.oddsfeed.provider.mock;

import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.Odds;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;

public final class LateGoal implements MockScenario {

  static final Duration TRIGGER_WINDOW = Duration.ofMinutes(10);
  private static final double MULTIPLIER = 0.5;

  @Override
  public String id() {
    return "LateGoal";
  }

  @Override
  public boolean canApply(MockOddsProvider.MockEvent event, Instant now) {
    if (event.status != EventLifecycleStatus.IN_PLAY) {
      return false;
    }
    Duration remaining = Duration.between(now, event.endAt);
    return !remaining.isNegative() && remaining.compareTo(TRIGGER_WINDOW) <= 0;
  }

  @Override
  public void apply(
      MockOddsProvider.MockEvent event, Instant now, Random rng, MockOddsProvider provider) {
    MockOddsProvider.MockMarket market = event.markets.values().iterator().next();
    List<MockOddsProvider.MockSelection> selections = List.copyOf(market.selections.values());
    MockOddsProvider.MockSelection target = selections.get(rng.nextInt(selections.size()));
    Odds previous = target.currentOdds;
    BigDecimal shortened =
        previous
            .decimal()
            .multiply(BigDecimal.valueOf(MULTIPLIER))
            .max(BigDecimal.valueOf(OddsSimulator.MIN_ODDS))
            .setScale(Odds.SCALE, RoundingMode.HALF_EVEN);
    Odds next = Odds.ofDecimal(shortened);
    target.currentOdds = next;
    provider.emit(
        event.summary.eventId(),
        new ProviderEvent.OddsUpdated(
            event.summary.eventId(), market.marketId, target.selectionId, previous, next, now));
  }
}
