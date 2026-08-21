package com.sportsbook.protocol.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventIdentityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void eachIdentityWrapsItsUuid() {
    UUID value = UUID.randomUUID();
    assertThat(EventId.of(value).value()).isEqualTo(value);
    assertThat(MarketId.of(value).value()).isEqualTo(value);
    assertThat(SelectionId.of(value).value()).isEqualTo(value);
  }

  @Test
  void identitiesRemainTypeDistinct() {
    UUID value = UUID.randomUUID();
    assertThat((Object) EventId.of(value)).isNotEqualTo(MarketId.of(value));
  }

  @Test
  void nullUuidIsRejected() {
    assertThatNullPointerException().isThrownBy(() -> EventId.of(null));
    assertThatNullPointerException().isThrownBy(() -> MarketId.of(null));
  }

  @Test
  void jsonUsesCanonicalUuidString() throws Exception {
    EventId id = EventId.of(UUID.fromString("018f0000-0000-7000-8000-000000000001"));
    assertThat(mapper.writeValueAsString(id)).isEqualTo("\"018f0000-0000-7000-8000-000000000001\"");
    assertThat(mapper.readValue(mapper.writeValueAsString(id), EventId.class)).isEqualTo(id);
  }
}
