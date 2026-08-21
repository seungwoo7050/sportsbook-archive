package com.sportsbook.oddsfeed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credentials accepted at the internal administration boundary. */
@ConfigurationProperties(prefix = "oddsfeed.security.internal")
public record InternalSecurityProperties(String apiKey) {

  public static final int MINIMUM_API_KEY_LENGTH = 32;

  public InternalSecurityProperties {
    if (apiKey == null || apiKey.isBlank() || apiKey.length() < MINIMUM_API_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "ADMIN_API_INTERNAL_KEY must contain at least " + MINIMUM_API_KEY_LENGTH + " characters");
    }
  }
}
