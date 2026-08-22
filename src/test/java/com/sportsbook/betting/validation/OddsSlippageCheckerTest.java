package com.sportsbook.betting.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.error.OddsDriftException;
import com.sportsbook.betting.policy.BettingPolicyProperties;
import com.sportsbook.protocol.value.Odds;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OddsSlippageCheckerTest {

  @Test
  void acceptsBoundaryAndRejectsWorsePrice() {
    BetLeg leg =
        BetLeg.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.00"));
    OddsSnapshotReader snapshots = mock(OddsSnapshotReader.class);
    BettingPolicyProperties policy = new BettingPolicyProperties(0, null, null, null, null);
    OddsSlippageChecker checker = new OddsSlippageChecker(snapshots, policy);

    when(snapshots.currentOdds(leg)).thenReturn(new BigDecimal("1.94"));
    assertThatCode(() -> checker.check(List.of(leg))).doesNotThrowAnyException();

    when(snapshots.currentOdds(leg)).thenReturn(new BigDecimal("1.9399"));
    assertThatThrownBy(() -> checker.check(List.of(leg))).isInstanceOf(OddsDriftException.class);
  }
}
