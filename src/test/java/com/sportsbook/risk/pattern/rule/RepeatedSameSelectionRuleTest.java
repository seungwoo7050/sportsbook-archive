package com.sportsbook.risk.pattern.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.RepeatedSelectionPolicy;
import com.sportsbook.risk.snapshot.PatternSnapshot;
import com.sportsbook.risk.snapshot.SnapshotSlot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RepeatedSameSelectionRuleTest {
  private static final SelectionId FIRST = SelectionId.of(new UUID(0, 3));
  private static final SelectionId SECOND = SelectionId.of(new UUID(0, 4));
  private static final PatternContext CONTEXT =
      new PatternContext(
          UserId.of(new UUID(0, 1)),
          BetId.of(new UUID(0, 2)),
          Money.krw(10),
          List.of(FIRST, SECOND),
          Instant.EPOCH);

  @Test
  void matchesOnlyAfterTheConfiguredCapIsExceeded() {
    RepeatedSameSelectionRule rule =
        new RepeatedSameSelectionRule(
            new RepeatedSelectionPolicy(true, Duration.ofDays(1), 2, PatternAction.REVIEW));

    assertThat(rule.evaluate(CONTEXT, snapshot(1, 0))).isEmpty();
    assertThat(rule.evaluate(CONTEXT, snapshot(2, 0)))
        .hasValueSatisfying(
            match -> {
              assertThat(match.rule()).isEqualTo(RepeatedSameSelectionRule.NAME);
              assertThat(match.reason()).contains(FIRST.toString());
            });
  }

  @Test
  void reportsTheFirstHotInputSelectionDeterministically() {
    RepeatedSameSelectionRule rule =
        new RepeatedSameSelectionRule(
            new RepeatedSelectionPolicy(true, Duration.ofDays(1), 1, PatternAction.BLOCK));

    assertThat(rule.evaluate(CONTEXT, snapshot(1, 1)))
        .hasValueSatisfying(match -> assertThat(match.reason()).contains(FIRST.toString()));
    assertThat(
            new RepeatedSameSelectionRule(RepeatedSelectionPolicy.defaults())
                .evaluate(CONTEXT, snapshot(100, 100)))
        .isEmpty();
  }

  private static PatternSnapshot snapshot(long first, long second) {
    return new PatternSnapshot(
        SnapshotSlot.success(0L),
        SnapshotSlot.success(List.of()),
        Map.of(FIRST, SnapshotSlot.success(first), SECOND, SnapshotSlot.success(second)));
  }
}
