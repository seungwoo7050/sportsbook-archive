package com.sportsbook.settlement.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("settlement.admin")
public record AdminCredentials(String apiKey) {

  public static final String CALLER = "admin-api";
  public static final String SERVICE_HEADER = "X-Service-Name";
  public static final String API_KEY_HEADER = "X-API-Key";
  private static final int MINIMUM_SECRET_LENGTH = 32;

  public AdminCredentials {
    if (apiKey == null || apiKey.isBlank() || apiKey.length() < MINIMUM_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "SETTLEMENT_ADMIN_API_KEY must contain at least 32 characters");
    }
  }

  @Override
  public String toString() {
    return "AdminCredentials[apiKey=<redacted>]";
  }
}
