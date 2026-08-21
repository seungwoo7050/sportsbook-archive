package com.sportsbook.protocol.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Bet slip shape per ADR-0008: Single / Multiple / System(K-of-N). System is generalized (minWins,
 * totalSelections) rather than enumerating named variants — Trixie / Yankee / Lucky 15 etc. stay as
 * frontend labels so adding a new K-of-N flavor needs zero protocol change.
 *
 * <p>Jackson polymorphism via {@code @JsonTypeInfo} + {@code @JsonSubTypes} produces a tagged shape
 * on the wire: {@code {"type":"SINGLE"}}, {@code {"type":"MULTIPLE"}}, {@code
 * {"type":"SYSTEM","minWins":2,"totalSelections":3}}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BetSlipType.Single.class, name = "SINGLE"),
  @JsonSubTypes.Type(value = BetSlipType.Multiple.class, name = "MULTIPLE"),
  @JsonSubTypes.Type(value = BetSlipType.System.class, name = "SYSTEM"),
})
public sealed interface BetSlipType
    permits BetSlipType.Single, BetSlipType.Multiple, BetSlipType.System {

  /** 1 selection per slip. */
  record Single() implements BetSlipType {}

  /** All N selections must win (parlay / accumulator). */
  record Multiple() implements BetSlipType {}

  /**
   * K-of-N partial-win slip. {@link #MAX_TOTAL_SELECTIONS} bounds the structurally possible shapes
   * (also keeps the C(total, min) combination explosion tractable). The runtime policy bound
   * ({@code betting.policy.max-selections} in application.yml) may be tighter.
   */
  record System(int minWins, int totalSelections) implements BetSlipType {

    public static final int MIN_TOTAL_SELECTIONS = 2;
    public static final int MAX_TOTAL_SELECTIONS = 15;

    public System {
      if (totalSelections < MIN_TOTAL_SELECTIONS || totalSelections > MAX_TOTAL_SELECTIONS) {
        throw new IllegalArgumentException(
            "System.totalSelections ("
                + totalSelections
                + ") must be in "
                + MIN_TOTAL_SELECTIONS
                + ".."
                + MAX_TOTAL_SELECTIONS);
      }
      if (minWins < 1 || minWins > totalSelections) {
        throw new IllegalArgumentException(
            "System.minWins ("
                + minWins
                + ") must be in 1..totalSelections ("
                + totalSelections
                + ")");
      }
    }
  }
}
