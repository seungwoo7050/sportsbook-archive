package com.sportsbook.oddsfeed.delivery;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The supplied idempotency or action identity is already bound to another request. */
@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException() {
    super("Idempotency identity is already bound to a different market action");
  }
}
