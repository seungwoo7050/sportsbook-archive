package com.sportsbook.risk.limit;

import com.sportsbook.protocol.value.UserId;
import java.util.OptionalLong;

/** Exact administrative overrides for one user's supported risk dimensions. */
public interface LimitOverrideStore {
  OptionalLong find(UserId userId, LimitOverrideField field);

  void set(UserId userId, LimitOverrideField field, long value);

  void clear(UserId userId, LimitOverrideField field);
}
