package com.sportsbook.gateway.routing;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.downstream.wallet")
public record WalletDownstreamProperties(URI uri, String apiKey) {

  private static final int MINIMUM_API_KEY_LENGTH = 32;

  public WalletDownstreamProperties {
    String scheme = uri == null ? null : uri.getScheme();
    String path = uri == null ? null : uri.getRawPath();
    if (uri == null
        || !uri.isAbsolute()
        || uri.isOpaque()
        || uri.getHost() == null
        || !("http".equals(scheme) || "https".equals(scheme))
        || uri.getUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null
        || !(path == null || path.isEmpty() || "/".equals(path))) {
      throw new IllegalArgumentException("gateway.downstream.wallet.uri must be an HTTP base URI");
    }
  }

  String requiredApiKey() {
    if (apiKey == null || apiKey.isBlank() || apiKey.length() < MINIMUM_API_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "GATEWAY_WALLET_API_KEY must contain at least 32 characters");
    }
    return apiKey;
  }

  @Override
  public String toString() {
    return "WalletDownstreamProperties[uri=" + uri + ", apiKey=<redacted>]";
  }
}
