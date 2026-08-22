package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JdbcTimestampsTest {

  @Test
  void convertsRequiredAndNullableInstantsToTypedJdbcValues() {
    Instant instant = Instant.parse("2026-08-22T01:02:03.004Z");

    assertThat(JdbcTimestamps.required(instant)).isEqualTo(Timestamp.from(instant));
    assertThat(JdbcTimestamps.nullable(instant)).isEqualTo(Timestamp.from(instant));
    assertThat(JdbcTimestamps.nullable(null)).isNull();
    assertThatNullPointerException().isThrownBy(() -> JdbcTimestamps.required(null));
  }
}
