package com.sportsbook.settlement.correction;

import static com.sportsbook.settlement.persistence.JdbcTimestamps.required;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ResultCandidateStore {

  private final JdbcTemplate jdbc;

  public ResultCandidateStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public RecordOutcome record(ResultCandidate candidate) {
    RecordOutcome existing = find(candidate.eventId(), candidate.fingerprint());
    if (existing != null) {
      return existing;
    }
    int inserted =
        jdbc.update(
            """
            insert into result_candidate (
                candidate_id, event_id, fingerprint, mode, settled_at, received_at,
                state, replaces_candidate_id, decided_at, decision_reason)
            values (?, ?, ?, ?, ?, ?, 'PENDING', ?, null, null)
            on conflict (event_id, fingerprint) do nothing
            """,
            candidate.candidateId(),
            candidate.eventId(),
            candidate.fingerprint(),
            candidate.mode().name(),
            required(candidate.settledAt()),
            required(candidate.receivedAt()),
            candidate.replacesCandidateId());
    if (inserted == 0) {
      RecordOutcome raced = find(candidate.eventId(), candidate.fingerprint());
      if (raced == null) {
        throw new IllegalStateException("Conflicting candidate insert has no durable row");
      }
      return raced;
    }
    List<Object[]> selections =
        candidate.outcomes().entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .map(
                entry ->
                    new Object[] {candidate.candidateId(), entry.getKey(), entry.getValue().name()})
            .toList();
    jdbc.batchUpdate(
        """
        insert into result_candidate_selection (candidate_id, selection_id, outcome)
        values (?, ?, ?)
        """,
        selections);
    return new RecordOutcome(
        RecordKind.CREATED, candidate.candidateId(), ResultCandidateState.PENDING);
  }

  private RecordOutcome find(UUID eventId, String fingerprint) {
    return jdbc
        .query(
            """
            select candidate_id, state from result_candidate
            where event_id = ? and fingerprint = ?
            """,
            (result, rowNumber) -> {
              ResultCandidateState state = ResultCandidateState.valueOf(result.getString("state"));
              RecordKind kind =
                  state == ResultCandidateState.PENDING
                      ? RecordKind.EXACT_REPLAY
                      : RecordKind.NO_CHANGE;
              return new RecordOutcome(kind, result.getObject("candidate_id", UUID.class), state);
            },
            eventId,
            fingerprint)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public enum RecordKind {
    CREATED,
    EXACT_REPLAY,
    NO_CHANGE
  }

  public record RecordOutcome(RecordKind kind, UUID candidateId, ResultCandidateState state) {}
}
