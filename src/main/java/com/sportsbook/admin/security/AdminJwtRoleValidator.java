package com.sportsbook.admin.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class AdminJwtRoleValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_ROLE =
      new OAuth2Error("invalid_token", "The role claim is invalid", null);

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    return AdminRole.fromClaim(jwt.getClaims().get("role")).isPresent()
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(INVALID_ROLE);
  }
}
