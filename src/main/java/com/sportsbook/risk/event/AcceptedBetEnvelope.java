package com.sportsbook.risk.event;

import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.RequestedSelection;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.reservation.ReservationFingerprint;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Typed, canonical boundary for one accepted-bet Kafka event. */
public record AcceptedBetEnvelope(RiskCheckCommand command, Instant requestedAt) {

  public AcceptedBetEnvelope {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(requestedAt, "requestedAt");
  }

  public static AcceptedBetEnvelope decode(String kafkaKey, byte[] payload, Instant observedAt) {
    BetPlacedRequested event = AvroCodec.decode(payload, BetPlacedRequested.class);
    return from(kafkaKey, event, observedAt);
  }

  static AcceptedBetEnvelope from(String kafkaKey, BetPlacedRequested event, Instant observedAt) {
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(observedAt, "observedAt");
    AcceptedBetExposure exposure = AcceptedBetExposure.from(event);

    UserId userId = UserId.of(uuid(event.getUserId(), "userId"));
    if (!userId.value().toString().equals(kafkaKey)) {
      throw new BetPlacedKeyMismatchException();
    }
    BetId betId = BetId.of(uuid(event.getBetId(), "betId"));
    List<SelectionId> selectionIds =
        event.getSelections().stream().map(AcceptedBetEnvelope::validateSelection).toList();
    if (selectionIds.stream().distinct().count() != selectionIds.size()) {
      throw new IllegalArgumentException("selectionIds must be unique");
    }

    com.sportsbook.protocol.event.Money wireStake =
        Objects.requireNonNull(event.getStake(), "stake");
    Currency currency = Currency.valueOf(required(wireStake.getCurrency(), "stake.currency"));
    new IdempotencyKey(required(event.getIdempotencyKey(), "idempotencyKey"));
    Instant requestedAt = Objects.requireNonNull(event.getRequestedAt(), "requestedAt");
    RiskCheckCommand command =
        new RiskCheckCommand(
            userId, betId, new Money(exposure.totalAmount(), currency), selectionIds, observedAt);
    return new AcceptedBetEnvelope(command, requestedAt);
  }

  public String reservationFingerprint() {
    return ReservationFingerprint.of(command);
  }

  private static SelectionId validateSelection(RequestedSelection selection) {
    Objects.requireNonNull(selection, "selection");
    EventId.of(uuid(selection.getEventId(), "eventId"));
    MarketId.of(uuid(selection.getMarketId(), "marketId"));
    Odds.ofDecimal(required(selection.getOddsAtSubmission(), "oddsAtSubmission"));
    return SelectionId.of(uuid(selection.getSelectionId(), "selectionId"));
  }

  private static UUID uuid(CharSequence value, String name) {
    String text = required(value, name);
    UUID uuid = UUID.fromString(text);
    if (!uuid.toString().equals(text)) {
      throw new IllegalArgumentException(name + " must be a canonical UUID");
    }
    return uuid;
  }

  private static String required(CharSequence value, String name) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value.toString();
  }
}
