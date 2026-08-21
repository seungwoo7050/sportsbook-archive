package com.sportsbook.oddsfeed.provider.mock;

import java.time.Instant;
import java.util.Random;

public interface MockScenario {

  String id();

  boolean canApply(MockOddsProvider.MockEvent event, Instant now);

  void apply(MockOddsProvider.MockEvent event, Instant now, Random rng, MockOddsProvider provider);
}
