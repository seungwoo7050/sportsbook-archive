package com.sportsbook.settlement.admin;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/revisions")
public final class AdminRevisionController {

  private final AdminRevisionCommands commands;

  public AdminRevisionController(AdminRevisionCommands commands) {
    this.commands = commands;
  }

  @PostMapping("/{revisionId}/retry")
  ResponseEntity<AdminRevisionCommands.Receipt> retry(
      @RequestHeader("Idempotency-Key") UUID idempotencyKey, @PathVariable UUID revisionId) {
    return ResponseEntity.accepted().body(commands.retry(idempotencyKey, revisionId));
  }
}
