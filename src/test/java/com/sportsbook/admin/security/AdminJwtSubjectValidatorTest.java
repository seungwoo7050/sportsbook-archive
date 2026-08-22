package com.sportsbook.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AdminJwtSubjectValidatorTest {

  private final AdminJwtSubjectValidator validator = new AdminJwtSubjectValidator();

  @Test
  void acceptsBoundedNonBlankSubjects() {
    assertThat(validator.validate(token("a")).hasErrors()).isFalse();
    assertThat(validator.validate(token("x".repeat(128))).hasErrors()).isFalse();
  }

  @Test
  void rejectsMissingBlankOrOversizedSubjects() {
    assertThat(validator.validate(token(null)).hasErrors()).isTrue();
    assertThat(validator.validate(token("")).hasErrors()).isTrue();
    assertThat(validator.validate(token("   ")).hasErrors()).isTrue();
    assertThat(validator.validate(token("x".repeat(129))).hasErrors()).isTrue();
  }

  @Test
  void rejectsWhitespaceEdgesAndControlCharacters() {
    assertThat(validator.validate(token(" operator")).hasErrors()).isTrue();
    assertThat(validator.validate(token("operator ")).hasErrors()).isTrue();
    assertThat(validator.validate(token("operator\nadmin")).hasErrors()).isTrue();
    assertThat(validator.validate(token("operator\u0000admin")).hasErrors()).isTrue();
  }

  private static Jwt token(String subject) {
    Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").claim("role", "ADMIN");
    if (subject != null) {
      builder.subject(subject);
    }
    return builder.build();
  }
}
