package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class CandidateRejectionTest {

  @Test
  void rejectsOnlyPendingCandidatesWithAnOperatorReason() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    assertThat(
            new ResultCandidateStore(jdbc)
                .reject(UUID.randomUUID(), Instant.EPOCH, "  bad source  "))
        .isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), parameters.capture());
    assertThat(sql.getValue()).contains("state = 'REJECTED'", "state = 'PENDING'");
    assertThat(parameters.getValue()[0]).isInstanceOf(Timestamp.class);
    assertThat(parameters.getValue()).contains("bad source");
  }

  @Test
  void rejectsBlankReasonsBeforePersistence() {
    ResultCandidateStore store = new ResultCandidateStore(mock(JdbcTemplate.class));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> store.reject(UUID.randomUUID(), Instant.EPOCH, " "));
  }
}
