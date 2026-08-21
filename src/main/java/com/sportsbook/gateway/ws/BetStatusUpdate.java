package com.sportsbook.gateway.ws;

import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.BetVoided;
import com.sportsbook.protocol.event.Money;
import java.time.Instant;

/** Private projection of a terminal bet state. */
public record BetStatusUpdate(
    String betId,
    String userId,
    String eventId,
    String status,
    String result,
    MoneyView amount,
    String reason,
    String revisionId,
    Long revisionNumber,
    Instant updatedAt) {

  public record MoneyView(long amount, String currency) {
    static MoneyView from(Money money) {
      return new MoneyView(money.getAmount(), money.getCurrency());
    }
  }

  static BetStatusUpdate settled(BetSettled event) {
    return new BetStatusUpdate(
        event.getBetId(),
        event.getUserId(),
        event.getEventId(),
        "SETTLED",
        event.getResult().name(),
        MoneyView.from(event.getPayout()),
        null,
        null,
        0L,
        event.getSettledAt());
  }

  static BetStatusUpdate voided(BetVoided event) {
    return new BetStatusUpdate(
        event.getBetId(),
        event.getUserId(),
        event.getEventId(),
        "VOIDED",
        null,
        MoneyView.from(event.getRefund()),
        event.getReason().name(),
        null,
        null,
        event.getVoidedAt());
  }
}
