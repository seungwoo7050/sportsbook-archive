package com.sportsbook.settlement.lifecycle;

import static com.sportsbook.settlement.persistence.JdbcTimestamps.nullable;
import static com.sportsbook.settlement.persistence.JdbcTimestamps.required;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LifecycleStore {

  private final JdbcTemplate jdbc;

  public LifecycleStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public RecordResult record(LifecycleObservation observation) {
    int inserted =
        jdbc.update(
            """
            insert into event_lifecycle_observation (
                observation_id, event_id, status, occurred_at,
                scheduled_start_at, received_at, fingerprint)
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (event_id, fingerprint) do nothing
            """,
            observation.observationId(),
            observation.eventId(),
            observation.status().name(),
            required(observation.occurredAt()),
            nullable(observation.scheduledStartAt()),
            required(observation.receivedAt()),
            observation.fingerprint());
    if (inserted == 0) {
      return RecordResult.EXACT_REPLAY;
    }
    if (!terminal(observation.status())) {
      return RecordResult.OBSERVED;
    }
    int latched =
        jdbc.update(
            """
            insert into event_lifecycle_tombstone (
                event_id, terminal_status, occurred_at, received_at, fingerprint)
            values (?, ?, ?, ?, ?)
            on conflict (event_id) do nothing
            """,
            observation.eventId(),
            observation.status().name(),
            required(observation.occurredAt()),
            required(observation.receivedAt()),
            observation.fingerprint());
    return latched == 1 ? RecordResult.TERMINAL_LATCHED : RecordResult.TERMINAL_ALREADY_LATCHED;
  }

  public Optional<LifecycleObservation> findTombstone(UUID eventId) {
    return jdbc
        .query(
            """
            select terminal_status, occurred_at, received_at, fingerprint
            from event_lifecycle_tombstone where event_id = ?
            """,
            (result, rowNumber) -> {
              String fingerprint = result.getString("fingerprint");
              return new LifecycleObservation(
                  UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8)),
                  eventId,
                  EventLifecycleStatus.valueOf(result.getString("terminal_status")),
                  result.getTimestamp("occurred_at").toInstant(),
                  null,
                  result.getTimestamp("received_at").toInstant(),
                  fingerprint);
            },
            eventId)
        .stream()
        .findFirst();
  }

  public List<LifecycleObservation> findActionableTombstones(int limit) {
    if (limit < 1 || limit > 1000) {
      throw new IllegalArgumentException("Lifecycle scan limit must be between 1 and 1000");
    }
    return jdbc.query(
        """
        select t.event_id, t.terminal_status, t.occurred_at, t.received_at, t.fingerprint
        from event_lifecycle_tombstone t
        where exists (
            select 1 from bet_selection s join bet b on b.bet_id = s.bet_id
            where s.event_id = t.event_id and b.status = 'PENDING'
              and not exists (
                  select 1 from settlement_attempt a where a.bet_id = b.bet_id))
        order by t.received_at, t.event_id limit ?
        """,
        (result, rowNumber) -> {
          UUID eventId = result.getObject("event_id", UUID.class);
          String fingerprint = result.getString("fingerprint");
          return new LifecycleObservation(
              UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8)),
              eventId,
              EventLifecycleStatus.valueOf(result.getString("terminal_status")),
              result.getTimestamp("occurred_at").toInstant(),
              null,
              result.getTimestamp("received_at").toInstant(),
              fingerprint);
        },
        limit);
  }

  private static boolean terminal(EventLifecycleStatus status) {
    return status == EventLifecycleStatus.CANCELLED || status == EventLifecycleStatus.POSTPONED;
  }

  public enum RecordResult {
    EXACT_REPLAY,
    OBSERVED,
    TERMINAL_LATCHED,
    TERMINAL_ALREADY_LATCHED
  }
}
