package com.sportsbook.wallet.persistence;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.wallet.domain.error.WalletBusyException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.Set;

/** Converts retryable PostgreSQL availability and concurrency failures without hiding defects. */
public final class PostgresFailureTranslator {

  private static final Set<String> RETRYABLE_STATES =
      Set.of("55P03", "57014", "40P01", "40001", "57P01", "57P02", "57P03", "53300");

  public static RuntimeException translate(IdempotencyKey key, RuntimeException failure) {
    if (failure instanceof WalletBusyException) {
      return failure;
    }
    if (isRetryable(failure)) {
      return new WalletBusyException(key, failure);
    }
    return failure;
  }

  public static boolean isRetryable(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof SQLTransientConnectionException
          || current instanceof SQLRecoverableException) {
        return true;
      }
      if (current instanceof SQLException sql) {
        String state = sql.getSQLState();
        if (state != null && (state.startsWith("08") || RETRYABLE_STATES.contains(state))) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }

  private PostgresFailureTranslator() {}
}
