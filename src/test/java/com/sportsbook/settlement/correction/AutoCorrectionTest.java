package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AutoCorrectionTest {

  @Test
  void intakeLinksAndAutoAcceptsAReplacementCandidate() {
    ResultCandidateStore store = mock(ResultCandidateStore.class);
    UUID acceptedId = UUID.randomUUID();
    when(store.findAcceptedCandidateId(any())).thenReturn(Optional.of(acceptedId));
    when(store.record(any()))
        .thenAnswer(
            invocation -> {
              ResultCandidate candidate = invocation.getArgument(0);
              return new ResultCandidateStore.RecordOutcome(
                  ResultCandidateStore.RecordKind.CREATED,
                  candidate.candidateId(),
                  ResultCandidateState.PENDING);
            });
    when(store.replaceAccepted(any(), any(), any())).thenReturn(true);
    MatchResultRecord result =
        new MatchResultRecord(
            UUID.randomUUID(),
            MatchOutcomeMode.COMPLETED,
            Map.of(),
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(1));

    assertThat(new ResultCandidateIntake(store).ingest(result))
        .isEqualTo(ResultCandidateIntake.IntakeResult.AUTO_CORRECTION_ACCEPTED);

    ArgumentCaptor<ResultCandidate> candidate = ArgumentCaptor.forClass(ResultCandidate.class);
    verify(store).record(candidate.capture());
    assertThat(candidate.getValue().replacesCandidateId()).isEqualTo(acceptedId);
  }

  @Test
  void replacementUsesCurrentSnapshotCompareAndSet() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    assertThat(
            new ResultCandidateStore(jdbc)
                .replaceAccepted(UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH))
        .isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, times(5)).update(sql.capture(), parameters.capture());
    assertThat(sql.getAllValues().get(0))
        .contains("m.accepted_candidate_id = ?", "c.state = 'PENDING'");
    assertThat(sql.getAllValues().get(3)).contains("state = 'SUPERSEDED'");
    assertThat(sql.getAllValues().get(4)).contains("state = 'ACCEPTED'");
    assertThat(parameters.getAllValues().get(3)[0]).isInstanceOf(Timestamp.class);
    assertThat(parameters.getAllValues().get(4)[0]).isInstanceOf(Timestamp.class);
  }

  @Test
  void firstCurrentRowWithNullCandidateIsNotMappedAsAccepted() {
    String database = "jdbc:h2:mem:first-current-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(database, "sa", ""));
    jdbc.execute(
        "create table match_result (event_id uuid primary key, accepted_candidate_id uuid)");
    UUID eventId = UUID.randomUUID();
    jdbc.update("insert into match_result (event_id) values (?)", eventId);

    assertThat(new ResultCandidateStore(jdbc).findAcceptedCandidateId(eventId)).isEmpty();
  }
}
