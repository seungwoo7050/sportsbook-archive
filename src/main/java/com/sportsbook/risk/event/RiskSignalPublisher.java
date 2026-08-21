package com.sportsbook.risk.event;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.pattern.PatternMatch;
import java.time.Instant;

/** Non-authoritative risk signal boundary; publication never decides admission. */
public interface RiskSignalPublisher {
  void publishLimit(
      UserId userId, LimitType type, long current, long limit, Money candidate, Instant occurredAt);

  void publishPattern(UserId userId, PatternMatch match, Instant occurredAt);
}
