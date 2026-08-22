package com.sportsbook.admin.security;

import java.time.Clock;
import java.time.Duration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

final class AdminJwtTimestampValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error MISSING_EXPIRY =
      new OAuth2Error("invalid_token", "The exp claim is required", null);

  private final JwtTimestampValidator timestamps;

  AdminJwtTimestampValidator() {
    this(Clock.systemUTC());
  }

  AdminJwtTimestampValidator(Clock clock) {
    timestamps = new JwtTimestampValidator(Duration.ZERO);
    timestamps.setClock(clock);
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    if (jwt.getExpiresAt() == null) {
      return OAuth2TokenValidatorResult.failure(MISSING_EXPIRY);
    }
    return timestamps.validate(jwt);
  }
}
