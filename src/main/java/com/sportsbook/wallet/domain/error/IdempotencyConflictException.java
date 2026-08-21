package com.sportsbook.wallet.domain.error;

import com.sportsbook.protocol.value.IdempotencyKey;

/** Raised when one idempotency key is retried with a different canonical request. */
public final class IdempotencyConflictException extends RuntimeException {

  private final String idempotencyKey;

  public IdempotencyConflictException(IdempotencyKey key) {
    super("Idempotency key was already used by another wallet request: " + key.value());
    this.idempotencyKey = key.value();
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }
}
