package com.sportsbook.admin.api;

import com.sportsbook.admin.audit.AdminAction;
import com.sportsbook.admin.audit.Audited;
import com.sportsbook.admin.client.SettlementCandidateReceipt;
import com.sportsbook.admin.client.SettlementClient;
import com.sportsbook.admin.client.SettlementRejectionPayload;
import com.sportsbook.admin.context.AdminContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/settlements/result-candidates/{candidateId}")
public class SettlementCandidateCommandController {

  private final SettlementClient settlements;

  public SettlementCandidateCommandController(SettlementClient settlements) {
    this.settlements = settlements;
  }

  @PostMapping("/approve")
  @PreAuthorize("hasAnyRole('ADMIN','TRADER')")
  @Audited(action = AdminAction.RESULT_CANDIDATE_APPROVE, target = "#candidateId")
  public SettlementCandidateReceipt approve(
      @PathVariable UUID candidateId, AdminContext context, HttpServletRequest servletRequest) {
    return settlements.approveCandidate(
        candidateId, AdminRequestHeaders.requireUuidIdempotencyKey(servletRequest));
  }

  @PostMapping("/reject")
  @PreAuthorize("hasAnyRole('ADMIN','TRADER')")
  @Audited(
      action = AdminAction.RESULT_CANDIDATE_REJECT,
      target = "#candidateId",
      reason = "#body.reason()")
  public SettlementCandidateReceipt reject(
      @PathVariable UUID candidateId,
      @RequestBody SettlementRejectionPayload body,
      AdminContext context,
      HttpServletRequest servletRequest) {
    return settlements.rejectCandidate(
        candidateId, AdminRequestHeaders.requireUuidIdempotencyKey(servletRequest), body);
  }
}
