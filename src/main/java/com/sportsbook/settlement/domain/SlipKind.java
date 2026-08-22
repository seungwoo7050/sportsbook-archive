package com.sportsbook.settlement.domain;

import com.sportsbook.protocol.domain.BetSlipType;
import java.util.Objects;

/** Flat persistence discriminator for the shared sealed slip shape. */
public enum SlipKind {
  SINGLE,
  MULTIPLE,
  SYSTEM;

  public static SlipKind from(BetSlipType type) {
    Objects.requireNonNull(type, "type");
    if (type instanceof BetSlipType.Single) {
      return SINGLE;
    }
    if (type instanceof BetSlipType.Multiple) {
      return MULTIPLE;
    }
    return SYSTEM;
  }

  public BetSlipType toProtocol(Integer minimumWins, Integer totalSelections) {
    return switch (this) {
      case SINGLE -> new BetSlipType.Single();
      case MULTIPLE -> new BetSlipType.Multiple();
      case SYSTEM -> new BetSlipType.System(minimumWins, totalSelections);
    };
  }
}
