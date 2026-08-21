package com.sportsbook.gateway.routing;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.downstream")
public record OddsFeedDownstreamProperties(URI oddsFeedUri) {

  public OddsFeedDownstreamProperties {
    String scheme = oddsFeedUri == null ? null : oddsFeedUri.getScheme();
    String path = oddsFeedUri == null ? null : oddsFeedUri.getRawPath();
    if (oddsFeedUri == null
        || !oddsFeedUri.isAbsolute()
        || oddsFeedUri.isOpaque()
        || oddsFeedUri.getHost() == null
        || !("http".equals(scheme) || "https".equals(scheme))
        || oddsFeedUri.getUserInfo() != null
        || oddsFeedUri.getRawQuery() != null
        || oddsFeedUri.getRawFragment() != null
        || !(path == null || path.isEmpty() || "/".equals(path))) {
      throw new IllegalArgumentException(
          "gateway.downstream.odds-feed-uri must be an HTTP base URI");
    }
  }
}
