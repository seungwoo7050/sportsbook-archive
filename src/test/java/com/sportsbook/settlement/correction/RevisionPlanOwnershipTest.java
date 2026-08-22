package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RevisionPlanOwnershipTest {

  @Test
  void detectsAnAlreadyOwnedBetRevision() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID betId = UUID.randomUUID();
    when(jdbc.queryForObject(
            org.mockito.ArgumentMatchers.contains("select exists"),
            org.mockito.ArgumentMatchers.eq(Boolean.class),
            org.mockito.ArgumentMatchers.eq(betId),
            org.mockito.ArgumentMatchers.eq(2L)))
        .thenReturn(true);

    assertThat(new RevisionPlanRepository(jdbc).exists(betId, 2)).isTrue();
  }
}
