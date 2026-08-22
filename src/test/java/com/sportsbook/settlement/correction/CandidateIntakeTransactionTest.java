package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringJUnitConfig(CandidateIntakeTransactionTest.Config.class)
class CandidateIntakeTransactionTest {
  @jakarta.annotation.Resource ResultCandidateIntake intake;
  @jakarta.annotation.Resource JdbcTemplate jdbc;

  @Test
  void rollsBackEvidenceWhenTheDecisionCrashesThenAcceptsRedelivery() {
    MatchResultRecord result =
        new MatchResultRecord(
            UUID.randomUUID(), MatchOutcomeMode.COMPLETED, Map.of(), Instant.EPOCH, Instant.EPOCH);

    assertThatThrownBy(() -> intake.ingest(result)).isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForObject("select count(*) from evidence", Integer.class)).isZero();
    assertThat(intake.ingest(result)).isEqualTo(ResultCandidateIntake.IntakeResult.FIRST_ACCEPTED);
  }

  @Configuration
  @EnableTransactionManagement
  static class Config {
    @Bean
    DataSource dataSource() {
      JdbcDataSource source = new JdbcDataSource();
      source.setURL("jdbc:h2:mem:candidate-tx;DB_CLOSE_DELAY=-1");
      new JdbcTemplate(source).execute("create table evidence (candidate_id uuid primary key)");
      return source;
    }

    @Bean
    JdbcTemplate jdbc(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }

    @Bean
    PlatformTransactionManager transactions(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    ResultCandidateStore store(JdbcTemplate jdbc) {
      return new CrashingStore(jdbc);
    }

    @Bean
    ResultCandidateIntake intake(ResultCandidateStore store) {
      return new ResultCandidateIntake(store);
    }
  }

  static class CrashingStore extends ResultCandidateStore {
    private final JdbcTemplate jdbc;
    private boolean first = true;

    CrashingStore(JdbcTemplate jdbc) {
      super(jdbc);
      this.jdbc = jdbc;
    }

    public Optional<AcceptedCandidate> findAcceptedCandidate(UUID eventId) {
      return Optional.empty();
    }

    @Override
    public RecordOutcome record(ResultCandidate candidate) {
      jdbc.update("insert into evidence (candidate_id) values (?)", candidate.candidateId());
      return new RecordOutcome(RecordKind.CREATED, candidate.candidateId(), candidate.state());
    }

    @Override
    public boolean acceptFirst(UUID candidateId, Instant decidedAt) {
      if (first) {
        first = false;
        throw new IllegalStateException("simulated decision crash");
      }
      return true;
    }
  }
}
