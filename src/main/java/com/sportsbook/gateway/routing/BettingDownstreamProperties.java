package com.sportsbook.gateway.routing;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.downstream")
public record BettingDownstreamProperties(URI bettingUri) {

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
}
