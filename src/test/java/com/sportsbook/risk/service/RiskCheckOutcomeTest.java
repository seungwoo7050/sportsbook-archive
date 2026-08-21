package com.sportsbook.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.pattern.PatternMatch;
import com.sportsbook.risk.policy.PatternAction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskCheckOutcomeTest {
  @Test
  void describesMonetaryAndSelectionCapacityRejections() {
    LimitRejection monetary =
        LimitRejection.rolling(LimitType.STAKE_DAILY, Currency.KRW, 900, 1000, 101);
    LimitRejection selections =
        LimitRejection.rolling(LimitType.SELECTIONS_PER_MINUTE, null, 29, 30, 2);

    assertThat(monetary.reason()).isEqualTo("STAKE_DAILY_LIMIT_EXCEEDED");
    assertThat(selections.reason()).isEqualTo("SELECTIONS_PER_MINUTE_LIMIT_EXCEEDED");
    assertThatThrownBy(() -> LimitRejection.rolling(LimitType.STAKE_DAILY, null, 900, 1000, 101))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> LimitRejection.rolling(LimitType.SELECTIONS_PER_MINUTE, Currency.USD, 29, 30, 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> LimitRejection.rolling(LimitType.STAKE_DAILY, Currency.KRW, 900, 1000, 100))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(LimitRejection.single(Currency.USD, 100, 101).reason())
        .isEqualTo("SINGLE_BET_MAX_EXCEEDED");
  }

  @Test
  void preservesOrderedPatternFlagsDefensively() {
    PatternMatch suspect = new PatternMatch("SUSPECT", PatternAction.SUSPECT, "signal");
    ArrayList<PatternMatch> source = new ArrayList<>(List.of(suspect));

    RiskCheckOutcome outcome = RiskCheckOutcome.approved(source);
    source.clear();

    assertThat(outcome.approved()).isTrue();
    assertThat(outcome.patterns()).containsExactly(suspect);
    assertThatThrownBy(() -> outcome.patterns().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void requiresOneBlockingCauseForRejectedOutcomes() {
    PatternMatch block = new PatternMatch("BLOCK", PatternAction.BLOCK, "blocked");
    RiskCheckOutcome patternOutcome = RiskCheckOutcome.rejectedByPattern(List.of(block));
    LimitRejection limit = LimitRejection.rolling(LimitType.STAKE_MONTHLY, Currency.USD, 20, 20, 1);

    assertThat(patternOutcome.approved()).isFalse();
    assertThat(RiskCheckOutcome.rejectedByLimit(limit).rejection()).isEqualTo(limit);
    assertThatThrownBy(() -> RiskCheckOutcome.approved(List.of(block)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskCheckOutcome.rejectedByPattern(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
