package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class AuditAspectTest {

  private static final UUID ACTION_ID = UUID.fromString("018f0000-0000-7000-8000-000000000061");
  private static final AdminContext CONTEXT =
      new AdminContext("operator-1", AdminRole.ADMIN, ACTION_ID, "trace-1");

  @Test
  void recordsStartedBeforeAndSuccessAfterTheDownstreamCall() {
    List<String> events = new ArrayList<>();
    AuditService audits = mock(AuditService.class);
    doAnswer(
            invocation -> {
              events.add("begin");
              return null;
            })
        .when(audits)
        .begin(CONTEXT, AdminAction.WALLET_REFUND.name(), "user-1", "operator request");
    doAnswer(
            invocation -> {
              events.add("complete");
              return null;
            })
        .when(audits)
        .complete(ACTION_ID, AuditOutcome.SUCCESS, 200);
    AuditedOperations operations = proxy(audits, events);

    assertThat(operations.success("user-1", CONTEXT)).isEqualTo("ok");

    assertThat(events).containsExactly("begin", "downstream", "complete");
  }

  @Test
  void finalizesAndRethrowsAnExplicitLocalFailureExactlyOnce() {
    List<String> events = new ArrayList<>();
    AuditService audits = mock(AuditService.class);
    AuditedOperations operations = proxy(audits, events);

    assertThatThrownBy(() -> operations.fail("user-2", CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid command");

    verify(audits).begin(CONTEXT, AdminAction.WALLET_REFUND.name(), "user-2", "operator request");
    verify(audits).complete(ACTION_ID, AuditOutcome.FAILED, 400);
  }

  @Test
  void neverInvokesDownstreamOrCompletionWhenStartedCannotPersist() {
    List<String> events = new ArrayList<>();
    AuditService audits = mock(AuditService.class);
    doThrow(
            new AuditPersistenceException(
                ACTION_ID,
                AuditPersistenceException.Phase.BEGIN,
                new IllegalStateException("db down")))
        .when(audits)
        .begin(CONTEXT, AdminAction.WALLET_REFUND.name(), "user-3", "operator request");
    AuditedOperations operations = proxy(audits, events);

    assertThatThrownBy(() -> operations.success("user-3", CONTEXT))
        .isInstanceOf(AuditPersistenceException.class);

    assertThat(events).isEmpty();
    verify(audits, never()).complete(ACTION_ID, AuditOutcome.SUCCESS, 200);
  }

  private static AuditedOperations proxy(AuditService audits, List<String> events) {
    AspectJProxyFactory factory = new AspectJProxyFactory(new AuditedOperations(events));
    factory.addAspect(new AuditAspect(audits));
    return factory.getProxy();
  }

  static class AuditedOperations {

    private final List<String> events;

    AuditedOperations(List<String> events) {
      this.events = events;
    }

    @Audited(action = AdminAction.WALLET_REFUND, target = "#p0", reason = "'operator request'")
    public String success(String userId, AdminContext context) {
      events.add("downstream");
      return "ok";
    }

    @Audited(action = AdminAction.WALLET_REFUND, target = "#p0", reason = "'operator request'")
    public String fail(String userId, AdminContext context) {
      events.add("downstream");
      throw new IllegalArgumentException("invalid command");
    }
  }
}
