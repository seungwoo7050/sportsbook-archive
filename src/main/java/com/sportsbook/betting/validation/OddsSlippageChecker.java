package com.sportsbook.betting.validation;

import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.error.OddsDriftException;
import com.sportsbook.betting.policy.BettingPolicyProperties;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OddsSlippageChecker {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final OddsSnapshotReader snapshots;
  private final BettingPolicyProperties policy;

  public OddsSlippageChecker(OddsSnapshotReader snapshots, BettingPolicyProperties policy) {
    this.snapshots = snapshots;
    this.policy = policy;
  }

  public void check(List<BetLeg> legs) {
    BigDecimal tolerance = HUNDRED.subtract(policy.slippageTolerancePercent());
    for (BetLeg leg : legs) {
      BigDecimal current = snapshots.currentOdds(leg);
      BigDecimal quoted = leg.oddsAtSubmission().decimal();
      if (current.multiply(HUNDRED).compareTo(quoted.multiply(tolerance)) < 0) {
        throw new OddsDriftException("Odds drifted beyond configured tolerance");
      }
    }
  }
}
