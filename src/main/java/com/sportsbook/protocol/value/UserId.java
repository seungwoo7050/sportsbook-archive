package com.sportsbook.protocol.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

/** Typed ID wrapper for an end User. See {@link EventId} for rationale. */
public record UserId(@JsonValue UUID value) {

  public UserId {
    Objects.requireNonNull(value, "value");
  }

  @JsonCreator
  public static UserId of(UUID value) {
    return new UserId(value);
  }
}
