package com.sportsbook.protocol.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

  @Test
  void catalogContainsSharedBoundaryCodes() {
    assertThat(ErrorCode.values())
        .containsExactly(
            ErrorCode.VALIDATION_FAILED,
            ErrorCode.ODDS_DRIFT,
            ErrorCode.DUPLICATE_BET,
            ErrorCode.INSUFFICIENT_BALANCE,
            ErrorCode.LIMIT_EXCEEDED,
            ErrorCode.EVENT_CLOSED,
            ErrorCode.SERVICE_UNAVAILABLE,
            ErrorCode.INTERNAL_ERROR);
  }

  @Test
  void statusesAreClientOrServerErrors() {
    assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::httpStatus))
        .allMatch(status -> status >= 400 && status < 600);
  }

  @Test
  void metadataIsStableAndComplete() {
    assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::title))
        .allMatch(title -> !title.isBlank());
    assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::type))
        .doesNotHaveDuplicates()
        .allMatch(uri -> uri.toString().startsWith("https://sportsbook/errors/"));
  }
}
