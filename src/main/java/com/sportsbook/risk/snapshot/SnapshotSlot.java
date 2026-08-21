package com.sportsbook.risk.snapshot;

import java.util.Objects;
import java.util.Optional;

/** One snapshot fact that either carries a value or a deferred Redis failure. */
public record SnapshotSlot<T>(T value, String error) {
  public SnapshotSlot {
    if ((value == null) == (error == null)) {
      throw new IllegalArgumentException("exactly one of value or error is required");
    }
    if (error != null && error.isBlank()) {
      throw new IllegalArgumentException("error must not be blank");
    }
  }

  public static <T> SnapshotSlot<T> success(T value) {
    return new SnapshotSlot<>(Objects.requireNonNull(value, "value"), null);
  }

  public static <T> SnapshotSlot<T> failure(String error) {
    return new SnapshotSlot<>(null, Objects.requireNonNull(error, "error"));
  }

  public T valueOrThrow() {
    if (error != null) {
      throw new IllegalStateException("risk snapshot unavailable: " + error);
    }
    return value;
  }

  public Optional<String> failure() {
    return Optional.ofNullable(error);
  }
}
