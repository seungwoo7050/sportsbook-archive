package com.sportsbook.betting.domain;

import com.sportsbook.protocol.domain.BetSlipType;
import java.util.Objects;

public enum SlipKind {
  SINGLE,
  MULTIPLE,
  SYSTEM;

  public static SlipKind of(BetSlipType slipType) {
    Objects.requireNonNull(slipType, "slipType");
    if (slipType instanceof BetSlipType.Single) {
      return SINGLE;
    }
    if (slipType instanceof BetSlipType.Multiple) {
      return MULTIPLE;
    }
    if (slipType instanceof BetSlipType.System) {
      return SYSTEM;
    }
    throw new IllegalArgumentException("Unsupported slip type");
  }
}
