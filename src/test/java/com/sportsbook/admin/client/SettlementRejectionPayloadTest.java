package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SettlementRejectionPayloadTest {

  @Test
  void trimsAReasonWithinTheProviderLimit() {
    assertThat(new SettlementRejectionPayload("  bad result  ").reason()).isEqualTo("bad result");
    assertThat(new SettlementRejectionPayload("r".repeat(256)).reason()).hasSize(256);
  }

  @Test
  void rejectsMissingBlankOversizedAndControlReasons() {
    assertThatThrownBy(() -> new SettlementRejectionPayload(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new SettlementRejectionPayload("   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SettlementRejectionPayload("r".repeat(257)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SettlementRejectionPayload("bad\nresult"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
