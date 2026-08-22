package com.sportsbook.settlement.admin;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin")
public final class AdminQueryController {

  private final AdminCandidateQueryRepository candidates;
  private final AdminRevisionQueryRepository revisions;

  public AdminQueryController(
      AdminCandidateQueryRepository candidates, AdminRevisionQueryRepository revisions) {
    this.candidates = candidates;
    this.revisions = revisions;
  }

  @GetMapping("/result-candidates/{candidateId}")
  AdminCandidateQueryRepository.View candidate(@PathVariable UUID candidateId) {
    return candidates
        .find(candidateId)
        .orElseThrow(() -> AdminControlException.notFound("Result candidate"));
  }

  @GetMapping("/revisions/{revisionId}")
  AdminRevisionQueryRepository.View revision(@PathVariable UUID revisionId) {
    return revisions
        .find(revisionId)
        .orElseThrow(() -> AdminControlException.notFound("Settlement revision"));
  }
}
