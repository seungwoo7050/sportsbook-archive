package com.sportsbook.betting.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.error.ErrorCode;
import org.junit.jupiter.api.Test;

class DurableOutcomeTest {

  @Test
  void retainsPersistedVerdictAndLookupDetail() {
    PersistedRejectionException rejection =
        new PersistedRejectionException(ErrorCode.LIMIT_EXCEEDED, "daily limit");

    assertThat(rejection.errorCode()).isEqualTo(ErrorCode.LIMIT_EXCEEDED);
    assertThat(rejection).hasMessage("daily limit");
    assertThat(new BetNotFoundException("missing")).hasMessage("missing");
  }
}
