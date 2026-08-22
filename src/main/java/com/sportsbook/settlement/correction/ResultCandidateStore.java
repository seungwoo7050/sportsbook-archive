package com.sportsbook.settlement.correction;

import static com.sportsbook.settlement.persistence.JdbcTimestamps.required;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

  @Transactional
  public boolean acceptFirst(UUID candidateId, java.time.Instant decidedAt) {
    int current =
        jdbc.update(
            """
            insert into match_result (
                event_id, mode, settled_at, received_at, accepted_candidate_id)
            select event_id, mode, settled_at, received_at, candidate_id
            from result_candidate where candidate_id = ? and state = 'PENDING'
            on conflict (event_id) do nothing
            """,
            candidateId);
    if (current == 0) {
      return false;
    }
    jdbc.update(
        """
        insert into match_selection_result (event_id, selection_id, outcome)
        select c.event_id, s.selection_id, s.outcome
        from result_candidate c join result_candidate_selection s
          on s.candidate_id = c.candidate_id
        where c.candidate_id = ?
        """,
        candidateId);
    int accepted =
        jdbc.update(
            """
            update result_candidate set state = 'ACCEPTED', decided_at = ?,
                decision_reason = 'FIRST_RESULT'
            where candidate_id = ? and state = 'PENDING'
            """,
            required(decidedAt),
            candidateId);
    if (accepted != 1) {
      throw new IllegalStateException("First result decision lost its candidate");
    }
    return true;
  }

  public Optional<UUID> findAcceptedCandidateId(UUID eventId) {
    return jdbc
        .query(
            """
            select accepted_candidate_id from match_result
            where event_id = ? and accepted_candidate_id is not null
            """,
            (result, rowNumber) -> result.getObject("accepted_candidate_id", UUID.class),
            eventId)
        .stream()
        .findFirst();
  }

  public Optional<AcceptedCandidate> findAcceptedCandidate(UUID eventId) {
    return jdbc
        .query(
            """
            select m.accepted_candidate_id, c.received_at
            from match_result m join result_candidate c
              on c.candidate_id = m.accepted_candidate_id
            where m.event_id = ? and m.accepted_candidate_id is not null
            """,
            (result, rowNumber) ->
                new AcceptedCandidate(
                    result.getObject("accepted_candidate_id", UUID.class),
                    result.getTimestamp("received_at").toInstant()),
            eventId)
        .stream()
        .findFirst();
  }

  @Transactional
  public boolean replaceAccepted(
      UUID candidateId, UUID expectedAcceptedId, java.time.Instant decidedAt) {
    int replaced =
        jdbc.update(
            """
            update match_result m set
                mode = c.mode, settled_at = c.settled_at, received_at = c.received_at,
                accepted_candidate_id = c.candidate_id
            from result_candidate c
            where c.candidate_id = ? and c.state = 'PENDING'
              and m.event_id = c.event_id and m.accepted_candidate_id = ?
            """,
            candidateId,
            expectedAcceptedId);
    if (replaced == 0) {
      return false;
    }
    jdbc.update(
        """
        delete from match_selection_result where event_id =
            (select event_id from result_candidate where candidate_id = ?)
        """,
        candidateId);
    jdbc.update(
        """
        insert into match_selection_result (event_id, selection_id, outcome)
        select c.event_id, s.selection_id, s.outcome
        from result_candidate c join result_candidate_selection s
          on s.candidate_id = c.candidate_id where c.candidate_id = ?
        """,
        candidateId);
    int superseded =
        jdbc.update(
            """
            update result_candidate set state = 'SUPERSEDED', decided_at = ?,
                decision_reason = 'AUTO_CORRECTION'
            where candidate_id = ? and state = 'ACCEPTED'
            """,
            required(decidedAt),
            expectedAcceptedId);
    if (superseded != 1) {
      throw new IllegalStateException("Replacement result lost its accepted candidate");
    }
    int accepted =
        jdbc.update(
            """
            update result_candidate set state = 'ACCEPTED', decided_at = ?,
                decision_reason = 'AUTO_CORRECTION'
            where candidate_id = ? and state = 'PENDING'
            """,
            required(decidedAt),
            candidateId);
    if (accepted != 1) {
      throw new IllegalStateException("Replacement result lost its pending candidate");
    }
    return true;
  }

  public boolean supersedeStale(UUID candidateId, java.time.Instant decidedAt) {
    return jdbc.update(
            """
            update result_candidate set state = 'SUPERSEDED', decided_at = ?,
                decision_reason = 'STALE_BASE'
            where candidate_id = ? and state = 'PENDING'
            """,
            required(decidedAt),
            candidateId)
        == 1;
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

  public record AcceptedCandidate(UUID candidateId, java.time.Instant receivedAt) {}
}
