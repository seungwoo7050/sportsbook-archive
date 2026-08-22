package com.sportsbook.admin.audit;

import com.sportsbook.admin.context.AdminContext;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private final AuditWriteRepository writes;

  public AuditService(AuditWriteRepository writes) {
    this.writes = writes;
  }

  public void begin(AdminContext context, String action, String target, String reason) {
    try {
      writes.begin(context, action, target, reason);
    } catch (RuntimeException failure) {
      throw new AuditPersistenceException(
          context.actionId(), AuditPersistenceException.Phase.BEGIN, failure);
    }
  }

  public AuditTerminalRecord complete(
      UUID actionId, AuditOutcome outcome, Integer httpStatus) {
    try {
      return writes.complete(actionId, outcome, httpStatus);
    } catch (RuntimeException failure) {
      throw new AuditPersistenceException(
          actionId, AuditPersistenceException.Phase.COMPLETE, failure);
    }
  }
}
