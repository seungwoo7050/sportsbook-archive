package com.sportsbook.risk.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatternActionTest {
  @Test
  void exposesStablePolicyVocabulary() {
    assertThat(PatternAction.values())
        .containsExactly(PatternAction.SUSPECT, PatternAction.REVIEW, PatternAction.BLOCK);
  }
}
