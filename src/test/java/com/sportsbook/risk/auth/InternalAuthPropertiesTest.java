package com.sportsbook.risk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.risk.auth.InternalAuthProperties.Caller;
import org.junit.jupiter.api.Test;

class InternalAuthPropertiesTest {
  private static final String BETTING = "b".repeat(32);
  private static final String ADMIN = "a".repeat(32);
  private static final String PLATFORM = "p".repeat(32);

  @Test
  void rejectsMissingShortAndDuplicateSecrets() {
    assertThatThrownBy(() -> new InternalAuthProperties(null, ADMIN, PLATFORM))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InternalAuthProperties("short", ADMIN, PLATFORM))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InternalAuthProperties(BETTING, BETTING, PLATFORM))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("distinct");
  }

  @Test
  void comparesCallerSpecificDigests() {
    InternalAuthProperties properties = new InternalAuthProperties(BETTING, ADMIN, PLATFORM);

    assertThat(properties.matches(Caller.BETTING_SERVICE, BETTING)).isTrue();
    assertThat(properties.matches(Caller.BETTING_SERVICE, ADMIN)).isFalse();
    assertThat(properties.matches(Caller.ADMIN_API, ADMIN)).isTrue();
    assertThat(properties.matches(Caller.PLATFORM, PLATFORM)).isTrue();
    assertThat(properties.toString()).doesNotContain(BETTING, ADMIN, PLATFORM);
  }

  @Test
  void resolvesOnlyCanonicalCallerNames() {
    assertThat(Caller.fromWire("betting-service")).contains(Caller.BETTING_SERVICE);
    assertThat(Caller.fromWire("admin-api")).contains(Caller.ADMIN_API);
    assertThat(Caller.fromWire("platform")).contains(Caller.PLATFORM);
    assertThat(Caller.fromWire("BETTING-SERVICE")).isEmpty();
  }
}
