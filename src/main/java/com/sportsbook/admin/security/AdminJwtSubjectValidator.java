package com.sportsbook.admin.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class AdminJwtSubjectValidator implements OAuth2TokenValidator<Jwt> {

  private static final int MAXIMUM_SUBJECT_LENGTH = 128;
  private static final OAuth2Error INVALID_SUBJECT =
      new OAuth2Error("invalid_token", "The sub claim is invalid", null);

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    String subject = jwt.getSubject();
    if (subject == null
        || subject.isBlank()
        || !subject.equals(subject.trim())
        || subject.codePointCount(0, subject.length()) > MAXIMUM_SUBJECT_LENGTH
        || subject.codePoints().anyMatch(Character::isISOControl)) {
      return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
    }
    return OAuth2TokenValidatorResult.success();
  }
}
