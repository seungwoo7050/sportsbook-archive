package com.sportsbook.protocol.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

/** Typed ID wrapper for a Selection within a Market. See {@link EventId} for rationale. */
public record SelectionId(@JsonValue UUID value) {

  public SelectionId {
    Objects.requireNonNull(value, "value");
  }

  @JsonCreator
  public static SelectionId of(UUID value) {
    return new SelectionId(value);
  }
}
