package com.sportsbook.betting.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.error.ErrorCode;
import org.junit.jupiter.api.Test;

class DependencyVerdictTest {

  @Test
  void distinguishesRetryableFailureFromRiskDecline() {
    Throwable cause = new IllegalStateException("timeout");
    DependencyUnavailableException unavailable =
        new DependencyUnavailableException("risk unavailable", cause);

    assertThat(unavailable.errorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    assertThat(unavailable).hasCause(cause);
    assertThat(new RiskLimitException(null).errorCode()).isEqualTo(ErrorCode.LIMIT_EXCEEDED);
  }
}
