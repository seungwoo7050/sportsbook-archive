package com.sportsbook.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class GatewayClaimsValidatorTest {

  private static final String SUBJECT = "123e4567-e89b-12d3-a456-426614174000";
  private final GatewayClaimsValidator validator = new GatewayClaimsValidator();

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1", "123E4567-E89B-12D3-A456-426614174000"})
  void rejectsMissingOrNonCanonicalSubject(String subject) {
    assertThat(validator.validate(jwt(subject, null)).hasErrors()).isTrue();
  }

  @ParameterizedTest
  @MethodSource("invalidRoles")
  void rejectsMalformedRoles(Object roles) {
    assertThat(validator.validate(jwt(SUBJECT, roles)).hasErrors()).isTrue();
  }

  @Test
  void acceptsIdentityWithoutRoles() {
    OAuth2TokenValidatorResult result = validator.validate(jwt(SUBJECT, null));
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void mapsCanonicalIdentityAndRoles() {
    Jwt jwt = jwt(SUBJECT, List.of("USER", "BET_OPERATOR"));
    AbstractAuthenticationToken authentication =
        new GatewayJwtAuthenticationConverter().convert(jwt);

    assertThat(validator.validate(jwt).hasErrors()).isFalse();
    assertThat(authentication.getName()).isEqualTo(SUBJECT);
    assertThat(authentication.getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_USER", "ROLE_BET_OPERATOR");
  }

  private static Stream<Object> invalidRoles() {
    return Stream.of(
        "USER",
        List.of("USER", "USER"),
        List.of("user"),
        List.of("UNSAFE-ROLE"),
        List.of("A".repeat(33)),
        List.of(1),
        IntStream.range(0, 17).mapToObj(index -> "R" + index).toList());
  }

  private static Jwt jwt(String subject, Object roles) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300));
    if (subject != null) {
      builder.subject(subject);
    }
    if (roles != null) {
      builder.claim("roles", roles);
    }
    return builder.build();
  }
}
