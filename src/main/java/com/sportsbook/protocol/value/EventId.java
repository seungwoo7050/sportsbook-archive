package com.sportsbook.protocol.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

/**
 * Typed ID wrapper for a betting Event (ADR-0003). UUID v7 generation lives in each consuming
 * service's persistence layer (Hibernate {@code @UuidGenerator(style = TIME)}); shared-protocol
 * only provides compile-time type safety.
 *
 * <p>{@code @JsonValue} on the component serializes as a raw UUID string ({@code "uuid-..."})
 * rather than a nested object ({@code {"value":"..."}}). DTO consumers see {@code "eventId":
 * "uuid"} which matches the typical REST shape.
 */
public record EventId(@JsonValue UUID value) {

  public EventId {
    Objects.requireNonNull(value, "value");
  }

  @JsonCreator
  public static EventId of(UUID value) {
    return new EventId(value);
  }
}
