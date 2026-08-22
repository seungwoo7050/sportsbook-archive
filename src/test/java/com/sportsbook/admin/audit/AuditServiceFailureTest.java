package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class AuditServiceFailureTest {

  private static final UUID ACTION_ID = UUID.fromString("018f0000-0000-7000-8000-000000000041");

  @Test
  void identifiesBeginPersistenceFailures() {
    AuditWriteRepository writes = mock(AuditWriteRepository.class);
    AuditService service = new AuditService(writes, mock(AdminActionPublisher.class));
    AdminContext context = new AdminContext("operator-1", AdminRole.ADMIN, ACTION_ID, "trace-1");
    doThrow(new DataAccessResourceFailureException("database unavailable"))
        .when(writes)
        .begin(context, "WALLET_REFUND", "user-1", "refund");

    AuditPersistenceException failure =
        catchThrowableOfType(
            () -> service.begin(context, "WALLET_REFUND", "user-1", "refund"),
            AuditPersistenceException.class);

    assertThat(failure.actionId()).isEqualTo(ACTION_ID);
    assertThat(failure.phase()).isEqualTo(AuditPersistenceException.Phase.BEGIN);
    assertThat(failure.getCause()).isInstanceOf(DataAccessResourceFailureException.class);
  }

  @Test
  void identifiesTerminalFinalizationFailures() {
    AuditWriteRepository writes = mock(AuditWriteRepository.class);
    AuditService service = new AuditService(writes, mock(AdminActionPublisher.class));
    doThrow(new IllegalStateException("lost STARTED claim"))
        .when(writes)
        .complete(ACTION_ID, AuditOutcome.UNKNOWN, null);

    AuditPersistenceException failure =
        catchThrowableOfType(
            () -> service.complete(ACTION_ID, AuditOutcome.UNKNOWN, null),
            AuditPersistenceException.class);

    assertThat(failure.actionId()).isEqualTo(ACTION_ID);
    assertThat(failure.phase()).isEqualTo(AuditPersistenceException.Phase.COMPLETE);
    assertThat(failure.getMessage()).doesNotContain("lost STARTED claim");
  }
}
