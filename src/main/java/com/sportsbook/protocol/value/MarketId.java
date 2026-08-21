package com.sportsbook.protocol.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

/** Typed ID wrapper for a Market within an Event. See {@link EventId} for rationale. */
public record MarketId(@JsonValue UUID value) {

  public MarketId {
    Objects.requireNonNull(value, "value");
  }

  @JsonCreator
  public static MarketId of(UUID value) {
    return new MarketId(value);
  }
}
