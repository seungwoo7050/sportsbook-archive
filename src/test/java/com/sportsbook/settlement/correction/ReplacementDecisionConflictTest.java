package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ReplacementDecisionConflictTest {

  @Test
  void failsWhenEitherCandidateTransitionIsLost() {
    UUID replacement = UUID.randomUUID();
    UUID accepted = UUID.randomUUID();
    JdbcTemplate lostAccepted = mock(JdbcTemplate.class);
    when(lostAccepted.update(anyString(), any(Object[].class))).thenReturn(1, 1, 1, 0);

    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                new ResultCandidateStore(lostAccepted)
                    .replaceAccepted(replacement, accepted, Instant.EPOCH));

    JdbcTemplate lostReplacement = mock(JdbcTemplate.class);
    when(lostReplacement.update(anyString(), any(Object[].class))).thenReturn(1, 1, 1, 1, 0);

    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                new ResultCandidateStore(lostReplacement)
                    .replaceAccepted(replacement, accepted, Instant.EPOCH));
  }
}
