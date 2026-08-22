package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.correction.CorrectionFanout;
import com.sportsbook.settlement.observability.SettlementMetrics;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.ResultFanout;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminOperationMetricsTest {

  @Test
  void timesAndCountsFailedCandidateActions() {
    AdminCandidateApproval approvals = mock(AdminCandidateApproval.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AdminCandidateCommands commands =
        new AdminCandidateCommands(
            approvals,
            mock(AdminCandidateRejection.class),
            mock(AcceptedResultRepository.class),
            mock(ResultFanout.class),
            mock(CorrectionFanout.class),
            new SettlementMetrics(registry));
    UUID candidateId = UUID.randomUUID();
    when(approvals.decide(any(), any())).thenThrow(new IllegalStateException("decision failed"));

    assertThatThrownBy(() -> commands.approve(UUID.randomUUID(), candidateId))
        .isInstanceOf(IllegalStateException.class);

    assertMetric(registry, "admin_action");
  }

  @Test
  void timesAndCountsFailedRevisionRetries() {
    AdminRevisionRetry retry = mock(AdminRevisionRetry.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AdminRevisionCommands commands =
        new AdminRevisionCommands(
            retry, mock(AdminRevisionQueryRepository.class), new SettlementMetrics(registry));
    when(retry.claim(any(), any())).thenThrow(new IllegalStateException("retry failed"));

    assertThatThrownBy(() -> commands.retry(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);

    assertMetric(registry, "admin_retry");
  }

  private static void assertMetric(SimpleMeterRegistry registry, String flow) {
    assertThat(
            registry
                .counter(SettlementMetrics.OPERATIONS, "flow", flow, "outcome", "failed")
                .count())
        .isEqualTo(1);
    assertThat(registry.timer(SettlementMetrics.DURATION, "flow", flow).count()).isOne();
  }
}
