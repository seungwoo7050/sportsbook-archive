package com.sportsbook.betting.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "betting.clients")
public record ClientProperties(
    String riskBaseUrl,
    String walletBaseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    String riskApiKey,
    String walletApiKey) {

  public ClientProperties {
    URI riskEndpoint = requireEndpoint(riskBaseUrl, "riskBaseUrl");
    URI walletEndpoint = requireEndpoint(walletBaseUrl, "walletBaseUrl");
    if (riskEndpoint.equals(walletEndpoint)) {
      throw new IllegalArgumentException("Risk and Wallet destinations must be distinct");
    }
    riskBaseUrl = riskEndpoint.toString();
    walletBaseUrl = walletEndpoint.toString();
    connectTimeout = connectTimeout == null ? Duration.ofMillis(200) : connectTimeout;
    readTimeout = readTimeout == null ? Duration.ofMillis(500) : readTimeout;
    riskApiKey = requireSecret(riskApiKey, "BETTING_RISK_API_KEY");
    walletApiKey = requireSecret(walletApiKey, "BETTING_WALLET_API_KEY");
    if (riskApiKey.equals(walletApiKey)) {
      throw new IllegalArgumentException("Dependency API keys must be distinct");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  private static URI requireEndpoint(String value, String name) {
    try {
      URI endpoint = URI.create(requireText(value, name)).normalize();
      String path = endpoint.getPath();
      boolean root = path == null || path.isEmpty() || path.equals("/");
      boolean http = endpoint.getScheme() != null && endpoint.getScheme().matches("https?");
      if (!endpoint.isAbsolute()
          || !http
          || endpoint.getHost() == null
          || endpoint.getUserInfo() != null
          || endpoint.getQuery() != null
          || endpoint.getFragment() != null
          || !root) {
        throw new IllegalArgumentException(name + " must be an absolute HTTP(S) origin");
      }
      String scheme = endpoint.getScheme().toLowerCase(Locale.ROOT);
      int port = endpoint.getPort();
      if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
        port = -1;
      }
      return new URI(
          scheme, null, endpoint.getHost().toLowerCase(Locale.ROOT), port, null, null, null);
    } catch (IllegalArgumentException | URISyntaxException invalid) {
      throw new IllegalArgumentException(name + " must be an absolute HTTP(S) origin", invalid);
    }
  }

  private static String requireSecret(String value, String name) {
    String secret = Objects.requireNonNull(value, name);
    if (secret.length() < 32 || secret.isBlank()) {
      throw new IllegalArgumentException(name + " must contain at least 32 characters");
    }
    return secret;
  }

  @Override
  public String toString() {
    return "ClientProperties["
        + "riskBaseUrl="
        + riskBaseUrl
        + ", walletBaseUrl="
        + walletBaseUrl
        + ", connectTimeout="
        + connectTimeout
        + ", readTimeout="
        + readTimeout
        + ", riskApiKey=<redacted>, walletApiKey=<redacted>]";
  }
}
