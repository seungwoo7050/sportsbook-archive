package com.sportsbook.risk.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.pattern.PatternMatch;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.service.LimitRejection;
import com.sportsbook.risk.service.RiskCheckOutcome;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiskCheckResponse(
    boolean approved, String rejectionReason, LimitInfo limit, List<PatternFlag> patterns) {

  public RiskCheckResponse {
    patterns = List.copyOf(patterns);
  }

  static RiskCheckResponse from(RiskCheckOutcome outcome) {
    LimitRejection rejection = outcome.rejection();
    return new RiskCheckResponse(
        outcome.approved(),
        rejection == null ? null : rejection.reason(),
        rejection == null ? null : LimitInfo.from(rejection),
        outcome.patterns().stream().map(PatternFlag::from).toList());
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record LimitInfo(
      LimitType type, Currency currency, long current, long limit, long requested) {
    static LimitInfo from(LimitRejection rejection) {
      return new LimitInfo(
          rejection.type(),
          rejection.currency(),
          rejection.current(),
          rejection.limit(),
          rejection.requested());
    }
  }

  public record PatternFlag(String rule, PatternAction action, String reason) {
    static PatternFlag from(PatternMatch match) {
      return new PatternFlag(match.rule(), match.action(), match.reason());
    }
  }
}
