package com.sportsbook.settlement.result;

import com.sportsbook.protocol.domain.SettlementResult;
import java.util.Optional;

public enum MatchOutcomeMode {
  COMPLETED,
  ABANDONED,
  VOIDED;

  public Optional<SettlementResult> resolve(SettlementResult reported) {
    return switch (this) {
      case COMPLETED -> Optional.ofNullable(reported);
      case ABANDONED -> Optional.ofNullable(reported).or(() -> Optional.of(SettlementResult.VOID));
      case VOIDED -> Optional.of(SettlementResult.VOID);
    };
  }
}
