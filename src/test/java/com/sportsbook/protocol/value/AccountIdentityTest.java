package com.sportsbook.protocol.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountIdentityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void accountIdentitiesWrapUuidValues() {
    UUID value = UUID.randomUUID();
    assertThat(BetId.of(value).value()).isEqualTo(value);
    assertThat(UserId.of(value).value()).isEqualTo(value);
  }

  @Test
  void accountIdentitiesRemainTypeDistinct() {
    UUID value = UUID.randomUUID();
    assertThat((Object) BetId.of(value)).isNotEqualTo(UserId.of(value));
  }

  @Test
  void nullUuidIsRejected() {
    assertThatNullPointerException().isThrownBy(() -> BetId.of(null));
    assertThatNullPointerException().isThrownBy(() -> UserId.of(null));
  }

  @Test
  void jsonRoundTripsAsUuidString() throws Exception {
    UserId id = UserId.of(UUID.randomUUID());
    assertThat(mapper.readValue(mapper.writeValueAsString(id), UserId.class)).isEqualTo(id);
  }
}
