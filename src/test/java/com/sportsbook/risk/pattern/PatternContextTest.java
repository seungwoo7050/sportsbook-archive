package com.sportsbook.risk.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.policy.PatternAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatternContextTest {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final BetId BET = BetId.of(new UUID(0, 2));
  private static final SelectionId SELECTION = SelectionId.of(new UUID(0, 3));

  @Test
  void retainsTypedCandidateFactsWithoutAliasingSelections() {
    List<SelectionId> selections = new ArrayList<>(List.of(SELECTION));
    PatternContext context =
        new PatternContext(USER, BET, Money.krw(1000), selections, Instant.EPOCH);
    selections.clear();

    assertThat(context.userId()).isEqualTo(USER);
    assertThat(context.betId()).isEqualTo(BET);
    assertThat(context.stake()).isEqualTo(Money.krw(1000));
    assertThat(context.selections()).containsExactly(SELECTION);
    assertThatThrownBy(() -> context.selections().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void validatesPatternEvidence() {
    PatternMatch match =
        new PatternMatch("rapid-betting", PatternAction.BLOCK, "threshold reached");

    assertThat(match.rule()).isEqualTo("rapid-betting");
    assertThat(match.action()).isEqualTo(PatternAction.BLOCK);
    assertThatThrownBy(() -> new PatternMatch(" ", PatternAction.REVIEW, "reason"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PatternContext(USER, BET, Money.krw(1), List.of(), Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
