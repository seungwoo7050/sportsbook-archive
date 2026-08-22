package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.correction.CorrectionFanout;
import com.sportsbook.settlement.observability.SettlementMetrics;
import com.sportsbook.settlement.result.AcceptedResult;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.ResultFanout;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminCandidateCommandsTest {

  private final AdminCandidateApproval approvals = mock(AdminCandidateApproval.class);
  private final AdminCandidateRejection rejections = mock(AdminCandidateRejection.class);
  private final AcceptedResultRepository acceptedResults = mock(AcceptedResultRepository.class);
  private final ResultFanout baseFanout = mock(ResultFanout.class);
  private final CorrectionFanout correctionFanout = mock(CorrectionFanout.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final AdminCandidateCommands commands =
      new AdminCandidateCommands(
          approvals,
          rejections,
          acceptedResults,
          baseFanout,
          correctionFanout,
          new SettlementMetrics(registry));

  @Test
  void redrivesBaseThenCorrectionFanoutForApprovedReplays() {
    UUID key = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    AdminAction action = action(key, candidateId, AdminAction.Outcome.CANDIDATE_APPROVED);
    AcceptedResult accepted =
        new AcceptedResult(eventId, candidateId, MatchOutcomeMode.VOIDED, Map.of(), Instant.EPOCH);
    when(approvals.decide(key, candidateId))
        .thenReturn(new AdminCandidateApproval.Decision(action, eventId, true));
    when(acceptedResults.findByEventId(eventId)).thenReturn(Optional.of(accepted));

    assertThat(commands.approve(key, candidateId))
        .isEqualTo(new AdminCandidateCommands.Receipt(key, "CANDIDATE_APPROVED", true));
    var ordered = inOrder(approvals, acceptedResults, baseFanout, correctionFanout);
    ordered.verify(approvals).decide(key, candidateId);
    ordered.verify(acceptedResults).findByEventId(eventId);
    ordered.verify(baseFanout).fanOut(accepted);
    ordered.verify(correctionFanout).fanOut(accepted);
    assertThat(counter("replay")).isEqualTo(1);
  }

  @Test
  void rejectsWithoutRunningSettlementFanout() {
    UUID key = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    AdminAction action = action(key, candidateId, AdminAction.Outcome.CANDIDATE_REJECTED);
    when(rejections.decide(key, candidateId, "BAD_RESULT"))
        .thenReturn(new AdminCandidateRejection.Decision(action, false));

    assertThat(commands.reject(key, candidateId, "BAD_RESULT").outcome())
        .isEqualTo("CANDIDATE_REJECTED");
    assertThat(counter("rejected")).isEqualTo(1);
    verifyNoInteractions(acceptedResults, baseFanout, correctionFanout);
  }

  private double counter(String outcome) {
    return registry
        .get(SettlementMetrics.OPERATIONS)
        .tags("flow", "admin_action", "outcome", outcome)
        .counter()
        .count();
  }

  private static AdminAction action(UUID key, UUID target, AdminAction.Outcome outcome) {
    AdminAction.Kind kind =
        outcome == AdminAction.Outcome.CANDIDATE_APPROVED
            ? AdminAction.Kind.CANDIDATE_APPROVE
            : AdminAction.Kind.CANDIDATE_REJECT;
    return new AdminAction(
        key,
        kind,
        target,
        AdminRequestFingerprint.create(kind, target, outcome.name()),
        outcome,
        null,
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
