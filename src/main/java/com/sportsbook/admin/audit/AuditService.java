package com.sportsbook.admin.audit;

import com.sportsbook.admin.context.AdminContext;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private final AuditWriteRepository writes;
  private final AdminActionPublisher publisher;

  public AuditService(AuditWriteRepository writes, AdminActionPublisher publisher) {
    this.writes = writes;
    this.publisher = publisher;
  }

  public void begin(AdminContext context, String action, String target, String reason) {
    try {
      writes.begin(context, action, target, reason);
    } catch (RuntimeException failure) {
      throw new AuditPersistenceException(
          context.actionId(), AuditPersistenceException.Phase.BEGIN, failure);
    }
  }

  public AuditTerminalRecord complete(UUID actionId, AuditOutcome outcome, Integer httpStatus) {
    try {
      AuditTerminalRecord terminal = writes.complete(actionId, outcome, httpStatus);
      publisher.publish(terminal);
      return terminal;
    } catch (RuntimeException failure) {
      throw new AuditPersistenceException(
          actionId, AuditPersistenceException.Phase.COMPLETE, failure);
    }
  }
}
