package com.sportsbook.gateway.routing;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "gateway.downstream")
public record BettingDownstreamProperties(URI bettingUri, String bettingApiKey) {

  private static final int MINIMUM_API_KEY_LENGTH = 32;

  public BettingDownstreamProperties(URI bettingUri) {
    this(bettingUri, null);
  }

  @ConstructorBinding
  public BettingDownstreamProperties {
    String scheme = bettingUri == null ? null : bettingUri.getScheme();
    String path = bettingUri == null ? null : bettingUri.getRawPath();
    if (bettingUri == null
        || !bettingUri.isAbsolute()
        || bettingUri.isOpaque()
        || bettingUri.getHost() == null
        || !("http".equals(scheme) || "https".equals(scheme))
        || bettingUri.getUserInfo() != null
        || bettingUri.getRawQuery() != null
        || bettingUri.getRawFragment() != null
        || !(path == null || path.isEmpty() || "/".equals(path))) {
      throw new IllegalArgumentException("gateway.downstream.betting-uri must be an HTTP base URI");
    }
  }

  String requiredApiKey() {
    if (bettingApiKey == null
        || bettingApiKey.isBlank()
        || bettingApiKey.length() < MINIMUM_API_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "GATEWAY_BETTING_API_KEY must contain at least 32 characters");
    }
    return bettingApiKey;
  }

  @Override
  public String toString() {
    return "BettingDownstreamProperties[bettingUri=" + bettingUri + ", bettingApiKey=<redacted>]";
  }
}
