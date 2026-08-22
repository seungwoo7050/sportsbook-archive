package com.sportsbook.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AdminJwtTimestampValidatorTest {

  private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");
  private final AdminJwtTimestampValidator validator =
      new AdminJwtTimestampValidator(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void acceptsOnlyATokenThatIsCurrentlyValid() {
    Jwt valid = token(NOW.minusSeconds(1), NOW.plusSeconds(60), NOW.minusSeconds(1));

    assertThat(validator.validate(valid).hasErrors()).isFalse();
  }

  @Test
  void rejectsMissingOrExpiredExpiryWithoutClockSkew() {
    Jwt missingExpiry = token(NOW.minusSeconds(1), null, NOW.minusSeconds(1));
    Jwt expired = token(NOW.minusSeconds(60), NOW.minusSeconds(1), NOW.minusSeconds(60));

    assertThat(validator.validate(missingExpiry).hasErrors()).isTrue();
    assertThat(validator.validate(expired).hasErrors()).isTrue();
  }

  @Test
  void rejectsTokensThatAreNotYetValid() {
    Jwt future = token(NOW, NOW.plusSeconds(120), NOW.plusSeconds(1));

    assertThat(validator.validate(future).hasErrors()).isTrue();
  }

  private static Jwt token(Instant issuedAt, Instant expiresAt, Instant notBefore) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("operator-1")
            .issuedAt(issuedAt)
            .notBefore(notBefore);
    if (expiresAt != null) {
      builder.expiresAt(expiresAt);
    }
    return builder.build();
  }
}
