package com.sportsbook.protocol.domain;

/**
 * V1 market shapes (ADR-0013). Asian handicap is excluded because ADR-0012 rules out
 * half-won/half-lost settlement results. Adding a market requires (a) extending this enum, (b)
 * updating settlement pricing, and (c) adding an OddsProvider mapping in odds-feed-service.
 */
public enum MarketType {
  MATCH_RESULT_1X2,
  TOTAL_OVER_UNDER,
  BOTH_TEAMS_TO_SCORE,
  DOUBLE_CHANCE,
}
