package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.wallet.domain.error.WalletBusyException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgresFailureTranslatorTest {

  private static final IdempotencyKey KEY = IdempotencyKey.of("busy:sqlstate");

  @Test
  void mapsEveryRetryablePostgresStateThroughNestedCauses() {
    for (String state :
        List.of(
            "55P03", "57014", "40P01", "40001", "08006", "08P01", "57P01", "57P02", "57P03",
            "53300")) {
      RuntimeException nested = new RuntimeException(new SQLException("retryable", state));

      assertThat(PostgresFailureTranslator.isRetryable(nested)).isTrue();
      assertThat(PostgresFailureTranslator.translate(KEY, nested))
          .isInstanceOf(WalletBusyException.class)
          .hasCause(nested);
    }
  }

  @Test
  void mapsConnectionExceptionsWithoutSqlStates() {
    for (SQLException connectionFailure :
        List.of(
            new SQLTransientConnectionException("pool timeout"),
            new SQLRecoverableException("broken connection"))) {
      RuntimeException nested = new RuntimeException(connectionFailure);

      assertThat(PostgresFailureTranslator.isRetryable(nested)).isTrue();
      assertThat(PostgresFailureTranslator.translate(KEY, nested))
          .isInstanceOf(WalletBusyException.class)
          .hasCause(nested);
    }
  }

  @Test
  void preservesBusyIdentityAndPermanentDatabaseFailures() {
    WalletBusyException busy = new WalletBusyException(KEY, new RuntimeException("busy"));
    assertThat(PostgresFailureTranslator.translate(KEY, busy)).isSameAs(busy);

    for (String state : List.of("23505", "42601")) {
      RuntimeException permanent =
          new RuntimeException(new SQLException("permanent database failure", state));

      assertThat(PostgresFailureTranslator.isRetryable(permanent)).isFalse();
      assertThat(PostgresFailureTranslator.translate(KEY, permanent)).isSameAs(permanent);
    }
  }
}
