package com.sportsbook.risk.pattern.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.SafeRedisNumber;
import com.sportsbook.risk.policy.SuddenStakePolicy;
import com.sportsbook.risk.snapshot.PatternSnapshot;
import com.sportsbook.risk.snapshot.SnapshotSlot;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SuddenStakeIncreaseRuleTest {
  private static final SelectionId SELECTION = SelectionId.of(new UUID(0, 3));

  @Test
  void requiresACompleteLookbackAndMatchesTheExactOddMedianThreshold() {
    SuddenStakeIncreaseRule rule = rule(3, 3);

    assertThat(rule.evaluate(context(100), snapshot(10, 20))).isEmpty();
    assertThat(rule.evaluate(context(59), snapshot(10, 20, 30))).isEmpty();
    assertThat(rule.evaluate(context(60), snapshot(10, 20, 30)))
        .hasValueSatisfying(
            match -> {
              assertThat(match.rule()).isEqualTo(SuddenStakeIncreaseRule.NAME);
              assertThat(match.action()).isEqualTo(PatternAction.REVIEW);
            });
  }

  @Test
  void usesAnExactEvenMedianWithoutOverflow() {
    assertThat(rule(3, 4).evaluate(context(89), snapshot(10, 20, 40, 100))).isEmpty();
    assertThat(rule(3, 4).evaluate(context(90), snapshot(10, 20, 40, 100))).isPresent();
    assertThat(rule(2, 3).evaluate(context(1), snapshot(0, 0, 0))).isEmpty();
    assertThat(
            rule(2, 3)
                .evaluate(
                    context(SafeRedisNumber.MAX_VALUE),
                    snapshot(
                        SafeRedisNumber.MAX_VALUE,
                        SafeRedisNumber.MAX_VALUE,
                        SafeRedisNumber.MAX_VALUE)))
        .isEmpty();
  }

  private static SuddenStakeIncreaseRule rule(int multiplier, int lookback) {
    return new SuddenStakeIncreaseRule(
        new SuddenStakePolicy(true, multiplier, lookback, PatternAction.REVIEW));
  }

  private static PatternContext context(long amount) {
    return new PatternContext(
        UserId.of(new UUID(0, 1)),
        BetId.of(new UUID(0, 2)),
        Money.krw(amount),
        List.of(SELECTION),
        Instant.EPOCH);
  }

  private static PatternSnapshot snapshot(long... stakes) {
    return new PatternSnapshot(
        SnapshotSlot.success(0L),
        SnapshotSlot.success(Arrays.stream(stakes).boxed().toList()),
        Map.of(SELECTION, SnapshotSlot.success(0L)));
  }
}
