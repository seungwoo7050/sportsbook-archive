package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CorrectionFanoutTest {

  @Test
  void directlySubmitsOnlyNewlyCapturedPlans() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CorrectionRevisionPreparer preparer = mock(CorrectionRevisionPreparer.class);
    RevisionExecutionRunner runner = mock(RevisionExecutionRunner.class);
    UUID newBetId = UUID.randomUUID();
    UUID existingBetId = UUID.randomUUID();
    var fixture = CorrectionFixtures.settledSingle(SettlementResult.LOST);
    var target =
        new ReplacementSnapshotProjector().project(fixture.bet(), fixture.accepted()).orElseThrow();
    RevisionPlan plan =
        RevisionPlan.allocate(target, new RevisionResolver().resolve(target), Instant.EPOCH);
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.MAX);
    when(jdbc.query(
            anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(newBetId, existingBetId));
    when(preparer.prepare(newBetId, fixture.accepted()))
        .thenReturn(Optional.of(new CorrectionRevisionPreparer.PreparedRevision(plan, lease)));
    when(preparer.prepare(existingBetId, fixture.accepted())).thenReturn(Optional.empty());
    when(runner.execute(plan, lease, false)).thenReturn(RevisionExecutionRunner.Result.APPLIED);

    var results =
        new CorrectionFanout(
                new CorrectionTargetRepository(jdbc),
                preparer,
                runner,
                new SettlementRuntimeProperties(null, null, null, 0))
            .fanOut(fixture.accepted());

    assertThat(results).containsExactly(RevisionExecutionRunner.Result.APPLIED);
    verify(preparer).prepare(newBetId, fixture.accepted());
    verify(preparer).prepare(existingBetId, fixture.accepted());
    verify(runner).execute(plan, lease, false);
  }
}
