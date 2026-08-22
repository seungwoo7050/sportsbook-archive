package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.betting.outbox.OutboxEvent;
import com.sportsbook.protocol.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class BetStoreTest {

  @Test
  void requestClaimsAndTerminalTransitionsAreTransactional() throws Exception {
    assertWriteTransaction("savePending", com.sportsbook.betting.domain.Bet.class);
    assertWriteTransaction(
        "savePreflightRejection",
        String.class,
        UUID.class,
        String.class,
        ErrorCode.class,
        String.class,
        Instant.class);
    assertWriteTransaction("acceptAndEnqueue", UUID.class, OutboxEvent.class, Instant.class);
  }

  @Test
  void recoveryReadsDoNotOpenWriteTransactions() throws Exception {
    Transactional annotation =
        BetStore.class
            .getMethod("findPlacementRequest", String.class)
            .getAnnotation(Transactional.class);

    assertThat(annotation.readOnly()).isTrue();
  }

  @Test
  void everyExternalSideEffectHasAnIndependentCheckpoint() throws Exception {
    assertWriteTransaction(
        "recordRiskReservation",
        UUID.class,
        Instant.class,
        String.class,
        boolean.class,
        Instant.class);
    assertWriteTransaction("confirmWallet", UUID.class, UUID.class, Instant.class);
    assertWriteTransaction("commitRisk", UUID.class, Instant.class);
    assertWriteTransaction("beginCompensation", UUID.class, Instant.class);
    assertWriteTransaction("completeRiskRelease", UUID.class, boolean.class, Instant.class);
    assertWriteTransaction("completeWalletRefund", UUID.class, UUID.class, Instant.class);
  }

  private static void assertWriteTransaction(String method, Class<?>... parameterTypes)
      throws Exception {
    Transactional annotation =
        BetStore.class.getMethod(method, parameterTypes).getAnnotation(Transactional.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.readOnly()).isFalse();
  }
}
