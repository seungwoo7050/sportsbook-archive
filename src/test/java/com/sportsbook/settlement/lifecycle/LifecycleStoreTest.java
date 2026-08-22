package com.sportsbook.settlement.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class LifecycleStoreTest {

  @Test
  void recordsEvidenceBeforeFirstTerminalTombstone() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 1);
    LifecycleObservation observation =
        LifecycleObservation.observe(
            UUID.randomUUID(),
            EventLifecycleStatus.CANCELLED,
            Instant.EPOCH,
            null,
            Instant.EPOCH.plusSeconds(1));

    assertThat(new LifecycleStore(jdbc).record(observation))
        .isEqualTo(LifecycleStore.RecordResult.TERMINAL_LATCHED);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, times(2)).update(sql.capture(), parameters.capture());
    assertThat(sql.getAllValues().get(0))
        .contains("event_lifecycle_observation", "on conflict (event_id, fingerprint) do nothing");
    assertThat(sql.getAllValues().get(1))
        .contains("event_lifecycle_tombstone", "on conflict (event_id) do nothing");
    assertThat(parameters.getAllValues().get(0)[3]).isInstanceOf(Timestamp.class);
    assertThat(parameters.getAllValues().get(0)[4]).isNull();
    assertThat(parameters.getAllValues().get(0)[5]).isInstanceOf(Timestamp.class);
    assertThat(parameters.getAllValues().get(1)[2]).isInstanceOf(Timestamp.class);
  }

  @Test
  void exactReplayDoesNotAttemptAnotherTerminalLatch() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
    LifecycleObservation replay =
        LifecycleObservation.observe(
            UUID.randomUUID(),
            EventLifecycleStatus.POSTPONED,
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            Instant.EPOCH.plusSeconds(1));

    assertThat(new LifecycleStore(jdbc).record(replay))
        .isEqualTo(LifecycleStore.RecordResult.EXACT_REPLAY);
    verify(jdbc).update(anyString(), any(Object[].class));
  }
}
