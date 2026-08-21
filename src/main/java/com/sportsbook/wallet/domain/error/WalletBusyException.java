package com.sportsbook.wallet.domain.error;

import com.sportsbook.protocol.value.IdempotencyKey;

/** Retryable failure raised when PostgreSQL cannot safely complete a wallet request in time. */
public class WalletBusyException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public WalletBusyException(IdempotencyKey key, Throwable cause) {
    super("Wallet is busy for idempotency key '" + key.value() + "'", cause);
  }
}
