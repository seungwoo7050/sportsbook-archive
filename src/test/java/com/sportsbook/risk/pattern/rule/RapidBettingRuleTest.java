package com.sportsbook.risk.pattern.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.RapidBettingPolicy;
import com.sportsbook.risk.snapshot.PatternSnapshot;
import com.sportsbook.risk.snapshot.SnapshotSlot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RapidBettingRuleTest {
  private static final SelectionId SELECTION = SelectionId.of(new UUID(0, 3));
  private static final PatternContext CONTEXT =
      new PatternContext(
          UserId.of(new UUID(0, 1)),
          BetId.of(new UUID(0, 2)),
          Money.krw(10),
          List.of(SELECTION),
          Instant.EPOCH);

  @Test
  void matchesWhenTheCandidateReachesTheThreshold() {
    RapidBettingRule rule =
        new RapidBettingRule(
            new RapidBettingPolicy(true, Duration.ofMinutes(1), 3, PatternAction.BLOCK));

    assertThat(rule.evaluate(CONTEXT, snapshot(1))).isEmpty();
    assertThat(rule.evaluate(CONTEXT, snapshot(2)))
        .hasValueSatisfying(
            match -> {
              assertThat(match.rule()).isEqualTo(RapidBettingRule.NAME);
              assertThat(match.action()).isEqualTo(PatternAction.BLOCK);
            });
  }

  @Test
  void disabledRulesDoNotConsumeUnavailableFacts() {
    RapidBettingRule rule = new RapidBettingRule(RapidBettingPolicy.defaults());
    PatternSnapshot unavailable =
        new PatternSnapshot(
            SnapshotSlot.failure("redis unavailable"),
            SnapshotSlot.success(List.of()),
            Map.of(SELECTION, SnapshotSlot.success(0L)));

    assertThat(rule.evaluate(CONTEXT, unavailable)).isEmpty();
  }

  private static PatternSnapshot snapshot(long recent) {
    return new PatternSnapshot(
        SnapshotSlot.success(recent),
        SnapshotSlot.success(List.of()),
        Map.of(SELECTION, SnapshotSlot.success(0L)));
  }
}
