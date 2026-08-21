package com.sportsbook.risk.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.snapshot.PatternSnapshot;
import com.sportsbook.risk.snapshot.SnapshotSlot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuleEngineTest {
  private static final SelectionId SELECTION = SelectionId.of(new UUID(0, 3));
  private static final PatternContext CONTEXT =
      new PatternContext(
          UserId.of(new UUID(0, 1)),
          BetId.of(new UUID(0, 2)),
          Money.krw(10),
          List.of(SELECTION),
          Instant.EPOCH);
  private static final PatternSnapshot SNAPSHOT =
      new PatternSnapshot(
          SnapshotSlot.success(0L),
          SnapshotSlot.success(List.of()),
          Map.of(SELECTION, SnapshotSlot.success(0L)));

  @Test
  void evaluatesEveryMatchInPriorityOrder() {
    AtomicInteger calls = new AtomicInteger();
    PatternRule later = rule("later", 20, PatternAction.REVIEW, calls);
    PatternRule blocker = rule("blocker", 10, PatternAction.BLOCK, calls);
    RuleEngine engine = new RuleEngine(List.of(later, blocker));

    assertThat(engine.ruleOrder()).containsExactly("blocker", "later");
    assertThat(engine.evaluate(CONTEXT, SNAPSHOT))
        .extracting(PatternMatch::rule)
        .containsExactly("blocker", "later");
    assertThat(calls).hasValue(2);
  }

  @Test
  void rejectsAmbiguousRuleNames() {
    PatternRule first = rule("same", 1, PatternAction.SUSPECT, new AtomicInteger());
    PatternRule second = rule("same", 2, PatternAction.REVIEW, new AtomicInteger());

    assertThatThrownBy(() -> new RuleEngine(List.of(first, second)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new RuleEngine(List.of()).evaluate(CONTEXT, SNAPSHOT)).isEmpty();
  }

  private static PatternRule rule(
      String name, int priority, PatternAction action, AtomicInteger calls) {
    return new PatternRule() {
      public String name() {
        return name;
      }

      public int priority() {
        return priority;
      }

      public Optional<PatternMatch> evaluate(PatternContext context, PatternSnapshot snapshot) {
        calls.incrementAndGet();
        return Optional.of(new PatternMatch(name, action, "matched"));
      }
    };
  }
}
