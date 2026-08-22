package com.sportsbook.admin.api;

import com.sportsbook.admin.audit.AdminAction;
import com.sportsbook.admin.audit.Audited;
import com.sportsbook.admin.client.SettlementClient;
import com.sportsbook.admin.client.SettlementRetryReceipt;
import com.sportsbook.admin.context.AdminContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/settlements/revisions/{revisionId}")
public class SettlementRevisionCommandController {

  private final SettlementClient settlements;

  public SettlementRevisionCommandController(SettlementClient settlements) {
    this.settlements = settlements;
  }

  @PostMapping("/retry")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasAnyRole('ADMIN','TRADER')")
  @Audited(action = AdminAction.SETTLEMENT_REVISION_RETRY, target = "#revisionId")
  public SettlementRetryReceipt retry(
      @PathVariable UUID revisionId, AdminContext context, HttpServletRequest servletRequest) {
    return settlements.retryRevision(
        revisionId, AdminRequestHeaders.requireUuidIdempotencyKey(servletRequest));
  }
}
