package com.sportsbook.protocol.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorRetryTest {

  @Test
  void unavailableDependencyUsesRetryableHttpStatus() {
    assertThat(ErrorCode.SERVICE_UNAVAILABLE.httpStatus()).isEqualTo(503);
  }

  @Test
  void businessRejectionsRemainClientErrors() {
    assertThat(ErrorCode.ODDS_DRIFT.httpStatus()).isBetween(400, 499);
    assertThat(ErrorCode.INSUFFICIENT_BALANCE.httpStatus()).isBetween(400, 499);
    assertThat(ErrorCode.LIMIT_EXCEEDED.httpStatus()).isBetween(400, 499);
  }
}
