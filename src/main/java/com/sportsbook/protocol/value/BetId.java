package com.sportsbook.protocol.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

/** Typed ID wrapper for a Bet slip. See {@link EventId} for rationale. */
public record BetId(@JsonValue UUID value) {

  public BetId {
    Objects.requireNonNull(value, "value");
  }

  @JsonCreator
  public static BetId of(UUID value) {
    return new BetId(value);
  }
}
