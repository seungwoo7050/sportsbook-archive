package com.sportsbook.settlement.event;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Separates poison contracts from failures that must leave the source offset retryable. */
public final class MessageFailureClassifier {

  public enum Disposition {
    PERMANENT,
    TRANSIENT
  }

  public Disposition classify(Throwable failure) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = failure;
    while (current != null && seen.add(current)) {
      if (current instanceof IllegalArgumentException
          || current instanceof StrictAvroDecoder.DecodeException) {
        return Disposition.PERMANENT;
      }
      current = current.getCause();
    }
    return Disposition.TRANSIENT;
  }
}
