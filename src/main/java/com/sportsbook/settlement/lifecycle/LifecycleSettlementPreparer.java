package com.sportsbook.settlement.lifecycle;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.domain.SettlementStatus;
import com.sportsbook.settlement.execution.SettlementAttemptDraft;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementExecution;
import com.sportsbook.settlement.persistence.BetRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LifecycleSettlementPreparer {

  private final BetRepository bets;
  private final SettlementAttemptRepository attempts;
  private final SettlementRuntimeProperties runtime;

  public LifecycleSettlementPreparer(
      BetRepository bets,
      SettlementAttemptRepository attempts,
      SettlementRuntimeProperties runtime) {
    this.bets = bets;
    this.attempts = attempts;
    this.runtime = runtime;
  }

  @Transactional
  public Optional<SettlementExecution> prepare(UUID betId, UUID eventId, String reason) {
    var bet = bets.findForUpdateById(betId).orElseThrow();
    if (bet.status() != SettlementStatus.PENDING || attempts.exists(betId)) {
      return Optional.empty();
    }
    var draft =
        SettlementAttemptDraft.wholeSlipVoid(
            bet.betId(), eventId, reason, totalExposure(bet.stake(), bet.slipType()));
    return attempts
        .claimPending(draft, runtime.leaseDuration())
        .map(claimed -> new SettlementExecution(claimed, bet.userId()))
        .or(
            () -> {
              throw new IllegalStateException("Initial lifecycle claim lost after bet lock");
            });
  }

  private static Money totalExposure(Money unitStake, BetSlipType slipType) {
    long lines = 1;
    if (slipType instanceof BetSlipType.System system) {
      lines = combinations(system.totalSelections(), system.minWins());
    }
    return unitStake.multiply(lines);
  }

  private static long combinations(int n, int k) {
    long result = 1;
    for (int factor = 1; factor <= k; factor++) {
      result = Math.multiplyExact(result, n - k + factor) / factor;
    }
    return result;
  }
}
