package com.sportsbook.betting.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.error.ErrorCode;
import org.junit.jupiter.api.Test;

class ValidationFailedExceptionTest {

  @Test
  void retainsSharedErrorCodeAndDetail() {
    ValidationFailedException exception = new ValidationFailedException("invalid stake");

    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThat(exception).hasMessage("invalid stake");
  }
}
