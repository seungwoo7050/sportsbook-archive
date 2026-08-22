package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseTimeSourceTest {

  @Test
  void readsTheTransactionTimestampFromTheDatabase() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Instant databaseTime = Instant.parse("2026-08-22T09:00:00Z");
    when(jdbc.queryForObject("select current_timestamp", Timestamp.class))
        .thenReturn(Timestamp.from(databaseTime));

    assertThat(new DatabaseTimeSource(jdbc).currentTimestamp()).isEqualTo(databaseTime);
  }
}
