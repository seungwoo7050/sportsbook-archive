package com.sportsbook.admin.api;

import com.sportsbook.admin.client.SettlementCandidateView;
import com.sportsbook.admin.client.SettlementClient;
import com.sportsbook.admin.client.SettlementRevisionView;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/settlements")
public class SettlementQueryController {

  private final SettlementClient settlements;

  public SettlementQueryController(SettlementClient settlements) {
    this.settlements = settlements;
  }

  @GetMapping("/result-candidates/{candidateId}")
  @PreAuthorize("hasAnyRole('ADMIN','TRADER','CS','READONLY')")
  public SettlementCandidateView getCandidate(@PathVariable UUID candidateId) {
    return settlements.getCandidate(candidateId);
  }

  @GetMapping("/revisions/{revisionId}")
  @PreAuthorize("hasAnyRole('ADMIN','TRADER','CS','READONLY')")
  public SettlementRevisionView getRevision(@PathVariable UUID revisionId) {
    return settlements.getRevision(revisionId);
  }
}
