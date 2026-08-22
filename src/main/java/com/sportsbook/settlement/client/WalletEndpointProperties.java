package com.sportsbook.settlement.client;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("settlement.wallet")
public record WalletEndpointProperties(URI baseUrl) {

  private static final URI DEFAULT_BASE_URL = URI.create("http://localhost:8081");

  public WalletEndpointProperties {
    baseUrl = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
    String path = baseUrl.getPath();
    boolean rootPath = path != null && (path.isEmpty() || "/".equals(path));
    if (!baseUrl.isAbsolute()
        || (!"http".equals(baseUrl.getScheme()) && !"https".equals(baseUrl.getScheme()))
        || baseUrl.getHost() == null
        || baseUrl.getRawUserInfo() != null
        || !rootPath
        || baseUrl.getRawQuery() != null
        || baseUrl.getRawFragment() != null) {
      throw new IllegalArgumentException(
          "settlement.wallet.base-url must be an HTTP(S) origin without credentials or suffixes");
    }
  }
}
