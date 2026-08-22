package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class CandidateCausalOrderTest {

  @Test
  void replacementMustFollowTheCurrentAcceptedSequence() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    UUID candidateId = UUID.randomUUID();
    UUID acceptedId = UUID.randomUUID();

    assertThat(
            new ResultCandidateStore(jdbc).replaceAccepted(candidateId, acceptedId, Instant.EPOCH))
        .isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, times(5)).update(sql.capture(), parameters.capture());
    assertThat(sql.getAllValues().get(0))
        .contains("c.replaces_candidate_id = ?", "c.candidate_sequence >");
    assertThat(parameters.getAllValues().get(0))
        .containsExactly(candidateId, acceptedId, acceptedId);
  }
}
