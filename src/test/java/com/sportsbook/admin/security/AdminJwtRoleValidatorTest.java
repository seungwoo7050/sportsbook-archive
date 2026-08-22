package com.sportsbook.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AdminJwtRoleValidatorTest {

  private final AdminJwtRoleValidator validator = new AdminJwtRoleValidator();

  @Test
  void acceptsEachExactOperatorRole() {
    for (AdminRole role : AdminRole.values()) {
      assertThat(validator.validate(token(role.name())).hasErrors()).isFalse();
    }
  }

  @Test
  void rejectsMissingUnknownAndNonStringRoles() {
    assertThat(validator.validate(token(null)).hasErrors()).isTrue();
    assertThat(validator.validate(token("")).hasErrors()).isTrue();
    assertThat(validator.validate(token("admin")).hasErrors()).isTrue();
    assertThat(validator.validate(token(" ADMIN ")).hasErrors()).isTrue();
    assertThat(validator.validate(token("OWNER")).hasErrors()).isTrue();
    assertThat(validator.validate(token(List.of("ADMIN"))).hasErrors()).isTrue();
  }

  private static Jwt token(Object role) {
    Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("operator-1");
    if (role != null) {
      builder.claim("role", role);
    }
    return builder.build();
  }
}
