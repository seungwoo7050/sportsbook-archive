package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MarketStatusPayloadTest {

  @Test
  void trimsAReasonWithinTheProviderLimit() {
    assertThat(new MarketStatusPayload("  feed investigation  ").reason())
        .isEqualTo("feed investigation");
    assertThat(new MarketStatusPayload("r".repeat(256)).reason()).hasSize(256);
  }

  @Test
  void rejectsMissingBlankAndOversizedReasons() {
    assertThatThrownBy(() -> new MarketStatusPayload(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new MarketStatusPayload("   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MarketStatusPayload("r".repeat(257)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
