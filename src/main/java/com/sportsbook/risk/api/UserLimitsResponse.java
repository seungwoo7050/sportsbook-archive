package com.sportsbook.risk.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import java.util.List;

/** Effective limits and their policy or override source for one account. */
public record UserLimitsResponse(UserId userId, List<Entry> limits) {
  public UserLimitsResponse {
    limits = List.copyOf(limits);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Entry(LimitType type, Currency currency, long value, Source source) {}

  public enum Source {
    POLICY,
    OVERRIDE
  }
}
