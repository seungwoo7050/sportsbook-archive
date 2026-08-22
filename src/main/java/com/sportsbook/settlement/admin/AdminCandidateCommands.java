package com.sportsbook.settlement.admin;

import com.sportsbook.settlement.correction.CorrectionFanout;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.ResultFanout;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AdminCandidateCommands {

  private final AdminCandidateApproval approvals;
  private final AdminCandidateRejection rejections;
  private final AcceptedResultRepository acceptedResults;
  private final ResultFanout baseFanout;
  private final CorrectionFanout correctionFanout;

  public AdminCandidateCommands(
      AdminCandidateApproval approvals,
      AdminCandidateRejection rejections,
      AcceptedResultRepository acceptedResults,
      ResultFanout baseFanout,
      CorrectionFanout correctionFanout) {
    this.approvals = approvals;
    this.rejections = rejections;
    this.acceptedResults = acceptedResults;
    this.baseFanout = baseFanout;
    this.correctionFanout = correctionFanout;
  }

  public Receipt approve(UUID idempotencyKey, UUID candidateId) {
    AdminCandidateApproval.Decision decision = approvals.decide(idempotencyKey, candidateId);
    var accepted =
        acceptedResults
            .findByEventId(decision.eventId())
            .orElseThrow(() -> new IllegalStateException("Approved result projection is missing"));
    baseFanout.fanOut(accepted);
    correctionFanout.fanOut(accepted);
    return new Receipt(
        decision.action().idempotencyKey(), decision.action().outcome().name(), decision.replay());
  }

  public Receipt reject(UUID idempotencyKey, UUID candidateId, String reason) {
    AdminCandidateRejection.Decision decision =
        rejections.decide(idempotencyKey, candidateId, reason);
    return new Receipt(
        decision.action().idempotencyKey(), decision.action().outcome().name(), decision.replay());
  }

  public record Receipt(UUID idempotencyKey, String outcome, boolean replay) {}
}
