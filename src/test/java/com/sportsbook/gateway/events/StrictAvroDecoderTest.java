package com.sportsbook.gateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.gateway.kafka.GatewayEventContractException;
import com.sportsbook.protocol.event.OddsChanged;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StrictAvroDecoderTest {

  @Test
  void decodesOneCompleteSharedProtocolRecord() {
    OddsChanged event = oddsChanged();

    assertThat(StrictAvroDecoder.decode(AvroTestSupport.encode(event), OddsChanged.class))
        .isEqualTo(event);
  }

  @Test
  void rejectsNullTruncatedAndTrailingPayloads() {
    byte[] encoded = AvroTestSupport.encode(oddsChanged());
    byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
    byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);

    assertThatThrownBy(() -> StrictAvroDecoder.decode(null, OddsChanged.class))
        .isInstanceOf(GatewayEventContractException.class);
    assertThatThrownBy(() -> StrictAvroDecoder.decode(truncated, OddsChanged.class))
        .isInstanceOf(GatewayEventContractException.class);
    assertThatThrownBy(() -> StrictAvroDecoder.decode(trailing, OddsChanged.class))
        .isInstanceOf(GatewayEventContractException.class)
        .hasMessageContaining("trailing bytes");
  }

  private static OddsChanged oddsChanged() {
    return OddsChanged.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setMarketId(UUID.randomUUID().toString())
        .setSelectionId(UUID.randomUUID().toString())
        .setPreviousOdds("1.8500")
        .setNewOdds("1.9000")
        .setChangedAt(Instant.parse("2026-08-21T00:00:00Z"))
        .build();
  }
}
