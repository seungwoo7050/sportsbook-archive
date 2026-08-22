package com.sportsbook.admin.security;

import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("admin.security.jwt")
public record AdminJwtProperties(@NotBlank String publicKey, String issuer) {

  public Optional<String> expectedIssuer() {
    return Optional.ofNullable(issuer).filter(value -> !value.isBlank());
  }
}
