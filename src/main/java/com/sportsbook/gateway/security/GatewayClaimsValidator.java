package com.sportsbook.gateway.security;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class GatewayClaimsValidator implements OAuth2TokenValidator<Jwt> {

  private static final int MAXIMUM_ROLES = 16;
  private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");
  private static final OAuth2Error INVALID_SUBJECT =
      new OAuth2Error("invalid_token", "sub must be a canonical UUID", null);
  private static final OAuth2Error INVALID_ROLES =
      new OAuth2Error("invalid_token", "roles must be a bounded unique string array", null);

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    if (!isCanonicalUuid(jwt.getSubject())) {
      return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
    }
    Object rolesClaim = jwt.getClaims().get("roles");
    if (rolesClaim == null) {
      return OAuth2TokenValidatorResult.success();
    }
    if (!(rolesClaim instanceof List<?> roles)
        || roles.size() > MAXIMUM_ROLES
        || roles.stream()
            .anyMatch(role -> !(role instanceof String value) || !ROLE.matcher(value).matches())
        || new HashSet<>(roles).size() != roles.size()) {
      return OAuth2TokenValidatorResult.failure(INVALID_ROLES);
    }
    return OAuth2TokenValidatorResult.success();
  }

  private static boolean isCanonicalUuid(String subject) {
    if (subject == null || subject.isBlank()) {
      return false;
    }
    try {
      return UUID.fromString(subject).toString().equals(subject);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
