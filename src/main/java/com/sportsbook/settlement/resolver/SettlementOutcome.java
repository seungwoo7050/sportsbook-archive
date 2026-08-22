package com.sportsbook.settlement.resolver;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.util.Objects;

/** Full base resolution snapshot stored and emitted for a bet. */
public record SettlementOutcome(
    SettlementResult result, Money payout, int survivingLines, int totalLines) {

  public SettlementOutcome {
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(payout, "payout");
    if (survivingLines < 0 || totalLines < 1 || survivingLines > totalLines) {
      throw new IllegalArgumentException("Invalid settlement outcome line counts");
    }
  }
}
