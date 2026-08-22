package com.sportsbook.settlement.lifecycle;

import static com.sportsbook.settlement.persistence.JdbcTimestamps.nullable;
import static com.sportsbook.settlement.persistence.JdbcTimestamps.required;

import com.sportsbook.protocol.event.EventLifecycleStatus;
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
