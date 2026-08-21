package com.sportsbook.protocol.domain;

/**
 * Settle-time outcome (ADR-0013). {@code HALF_WON} / {@code HALF_LOST} are deferred until Asian
 * handicap support (ADR-0012). {@code CASHED_OUT} is deferred until cash-out feature. {@code PUSH}
 * is the stake-refund case (e.g., O/U line exactly hit). {@code VOID} here means per-selection void
 * at settle time, distinct from {@link BetStatus#VOIDED} which is a whole-slip refund on event
 * cancellation.
 */
public enum SettlementResult {
  WON,
  LOST,
  PUSH,
  VOID,
}
