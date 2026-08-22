package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.persistence.BetRepository;
import com.sportsbook.settlement.persistence.DatabaseTimeSource;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CorrectionRevisionPreparerTest {

  @Test
  void locksAndPersistsTheResolvedReplacementSnapshot() {
    BetRepository bets = mock(BetRepository.class);
    AcceptedResultRepository acceptedResults = mock(AcceptedResultRepository.class);
    RevisionPlanRepository revisions = mock(RevisionPlanRepository.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    DatabaseTimeSource databaseTime = new DatabaseTimeSource(jdbc);
    var fixture =
        CorrectionFixtures.settledSingle(com.sportsbook.protocol.domain.SettlementResult.LOST);
    Instant createdAt = Instant.parse("2026-08-22T09:00:00Z");
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), createdAt.plusSeconds(30));
    when(bets.findForUpdateById(fixture.bet().betId())).thenReturn(Optional.of(fixture.bet()));
    when(acceptedResults.findByEventId(fixture.accepted().eventId()))
        .thenReturn(Optional.of(fixture.accepted()));
    when(jdbc.queryForObject("select current_timestamp", Timestamp.class))
        .thenReturn(Timestamp.from(createdAt));
    when(revisions.persist(any(), eq(Duration.ofSeconds(30))))
        .thenAnswer(
            call -> {
              RevisionPlan plan = call.getArgument(0);
              return new RevisionPlanRepository.Persisted(
                  plan.revisionId(), true, lease, createdAt);
            });

    var prepared =
        new CorrectionRevisionPreparer(
                bets,
                acceptedResults,
                revisions,
                new SettlementRuntimeProperties(null, null, null, 0),
                databaseTime)
            .prepare(fixture.bet().betId(), fixture.accepted())
            .orElseThrow();

    assertThat(prepared.lease()).isEqualTo(lease);
    assertThat(prepared.plan().createdAt()).isEqualTo(createdAt);
    assertThat(prepared.plan().target().sourceCandidateId())
        .isEqualTo(fixture.accepted().candidateId());
    assertThat(prepared.plan().target().previousPayout()).isEqualTo(Money.krw(200));
    assertThat(prepared.plan().newPayout()).isEqualTo(Money.krw(0));
  }
}
