package com.sportsbook.betting.api;

import com.sportsbook.betting.client.ClientProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class GatewayAuthProperties {

  private final byte[] apiKey;

  @Autowired
  public GatewayAuthProperties(
      @Value("${BETTING_GATEWAY_API_KEY}") String apiKey, ClientProperties clients) {
    this(apiKey, clients.riskApiKey(), clients.walletApiKey());
  }

  GatewayAuthProperties(String apiKey) {
    this(apiKey, null, null);
  }

  private GatewayAuthProperties(String apiKey, String riskKey, String walletKey) {
    if (apiKey == null || apiKey.isBlank() || apiKey.length() < 32) {
      throw new IllegalArgumentException(
          "BETTING_GATEWAY_API_KEY must contain at least 32 characters");
    }
    if (apiKey.equals(riskKey) || apiKey.equals(walletKey)) {
      throw new IllegalArgumentException("Gateway and dependency API keys must be distinct");
    }
    this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
  }

  boolean matches(String candidate) {
    byte[] supplied = candidate == null ? new byte[0] : candidate.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(apiKey, supplied);
  }

  @Override
  public String toString() {
    return "GatewayAuthProperties[apiKey=<redacted>]";
  }
}
