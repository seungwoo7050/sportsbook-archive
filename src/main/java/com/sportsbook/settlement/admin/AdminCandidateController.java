package com.sportsbook.settlement.admin;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/result-candidates")
public final class AdminCandidateController {

  private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

  private final AdminCandidateCommands commands;

  public AdminCandidateController(AdminCandidateCommands commands) {
    this.commands = commands;
  }

  @PostMapping("/{candidateId}/approve")
  AdminCandidateCommands.Receipt approve(
      @RequestHeader(IDEMPOTENCY_HEADER) UUID idempotencyKey, @PathVariable UUID candidateId) {
    return commands.approve(idempotencyKey, candidateId);
  }

  @PostMapping("/{candidateId}/reject")
  AdminCandidateCommands.Receipt reject(
      @RequestHeader(IDEMPOTENCY_HEADER) UUID idempotencyKey,
      @PathVariable UUID candidateId,
      @RequestBody Rejection request) {
    return commands.reject(idempotencyKey, candidateId, request.reason());
  }

  record Rejection(String reason) {}
}
