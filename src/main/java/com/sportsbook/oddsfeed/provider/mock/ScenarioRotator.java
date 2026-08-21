package com.sportsbook.oddsfeed.provider.mock;

import com.sportsbook.oddsfeed.config.MockProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("mock")
public class ScenarioRotator {

  private final MockProperties properties;
  private final MockOddsProvider provider;
  private final Clock clock;
  private final List<MockScenario> scenarios;
  private final Random random;

  public ScenarioRotator(MockProperties properties, MockOddsProvider provider, Clock clock) {
    this.properties = properties;
    this.provider = provider;
    this.clock = clock;
    this.scenarios =
        List.of(new LateGoal(), new MatchPostponed(), new SuddenMarketSuspend(), new OddsCrash());
    this.random =
        properties.randomSeed() == 0 ? new Random() : new Random(properties.randomSeed() + 1);
  }

  @Scheduled(
      fixedRateString = "${oddsfeed.mock.scenarios.rotation-interval-seconds:60}",
      timeUnit = TimeUnit.SECONDS)
  void scheduledRotate() {
    if (properties.scenarios().autoRotate()) {
      rotateOnce(clock.instant());
    }
  }

  void rotateOnce(Instant now) {
    MockScenario scenario = scenarios.get(random.nextInt(scenarios.size()));
    for (MockOddsProvider.MockEvent event : provider.activeEvents()) {
      if (scenario.canApply(event, now)) {
        scenario.apply(event, now, random, provider);
        return;
      }
    }
  }

  List<MockScenario> scenarios() {
    return scenarios;
  }
}
