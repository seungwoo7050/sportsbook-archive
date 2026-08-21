package com.sportsbook.oddsfeed.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.event.MarketStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarketActionFingerprintTest {

  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID MARKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Test
  void locksCanonicalSha256Fingerprint() {
    assertThat(
            MarketActionFingerprint.request(
                EVENT_ID, MARKET_ID, MarketStatus.SUSPENDED, "incident"))
        .isEqualTo("45e1241da4bf5626acd9ea4c72e71f8aae4c0f19d5195ae6a5a87e6c52c8255f");
  }

  @Test
  void actionStatusAndReasonAreFingerprintInputs() {
    String baseline =
        MarketActionFingerprint.request(EVENT_ID, MARKET_ID, MarketStatus.SUSPENDED, "incident");

    assertThat(
            MarketActionFingerprint.request(EVENT_ID, MARKET_ID, MarketStatus.CLOSED, "incident"))
        .isNotEqualTo(baseline);
    assertThat(
            MarketActionFingerprint.request(
                EVENT_ID, MARKET_ID, MarketStatus.SUSPENDED, "different"))
        .isNotEqualTo(baseline);
  }
}
