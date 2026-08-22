package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.correction.ResultCandidateIntake;
import com.sportsbook.settlement.correction.ResultCandidateStore;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresFirstCandidateRaceIntegrationTest.RaceConfiguration.class)
class PostgresFirstCandidateRaceIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private ResultCandidateIntake intake;

  @Test
  void acceptsOneConcurrentFirstResultAndSupersedesTheLoser() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-22T04:00:00Z");
    var workers = Executors.newFixedThreadPool(2);

    try {
      var one =
          workers.submit(
              () -> intake.ingest(result(eventId, selectionId, SettlementResult.WON, now)));
      var two =
          workers.submit(
              () -> intake.ingest(result(eventId, selectionId, SettlementResult.LOST, now)));

      assertThat(List.of(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(
              ResultCandidateIntake.IntakeResult.FIRST_ACCEPTED,
              ResultCandidateIntake.IntakeResult.CORRECTION_SUPERSEDED);
      assertThat(
              jdbc.queryForList(
                  "select state from result_candidate where event_id = ?", String.class, eventId))
          .containsExactlyInAnyOrder("ACCEPTED", "SUPERSEDED");
    } finally {
      workers.shutdownNow();
    }
  }

  private MatchResultRecord result(
      UUID eventId, UUID selectionId, SettlementResult outcome, Instant now) {
    return new MatchResultRecord(
        eventId, MatchOutcomeMode.COMPLETED, Map.of(selectionId, outcome), now, now);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RaceConfiguration {
    @Bean
    @Primary
    ResultCandidateStore raceStore(JdbcTemplate jdbc) {
      return new BarrierStore(jdbc);
    }
  }

  static class BarrierStore extends ResultCandidateStore {
    private final CyclicBarrier barrier = new CyclicBarrier(2);

    BarrierStore(JdbcTemplate jdbc) {
      super(jdbc);
    }

    @Override
    public Optional<AcceptedCandidate> findAcceptedCandidate(UUID eventId) {
      Optional<AcceptedCandidate> accepted = super.findAcceptedCandidate(eventId);
      try {
        barrier.await(2, TimeUnit.SECONDS);
        return accepted;
      } catch (Exception exception) {
        throw new AssertionError(exception);
      }
    }
  }
}
