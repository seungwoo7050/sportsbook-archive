package com.sportsbook.admin.client;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("admin.downstream")
public record DownstreamProperties(
    @NotNull URI walletBaseUrl,
    @NotNull URI riskBaseUrl,
    @NotNull URI oddsFeedBaseUrl,
    @NotNull URI settlementBaseUrl,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout) {

  public DownstreamProperties {
    Stream.of(walletBaseUrl, riskBaseUrl, oddsFeedBaseUrl, settlementBaseUrl)
        .filter(uri -> uri != null)
        .forEach(DownstreamProperties::requireHttpOrigin);
    requirePositive(connectTimeout);
    requirePositive(readTimeout);
  }

  private static void requireHttpOrigin(URI uri) {
    boolean supportedScheme = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
    if (!uri.isAbsolute()
        || !supportedScheme
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new IllegalArgumentException("Downstream base URLs must be HTTP origins");
    }
  }

  private static void requirePositive(Duration duration) {
    if (duration != null && (duration.isZero() || duration.isNegative())) {
      throw new IllegalArgumentException("Downstream timeouts must be positive");
    }
  }
}
