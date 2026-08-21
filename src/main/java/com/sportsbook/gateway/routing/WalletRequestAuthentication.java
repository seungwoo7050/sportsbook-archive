package com.sportsbook.gateway.routing;

import com.sportsbook.gateway.security.GatewayHeaders;
import org.springframework.web.servlet.function.ServerRequest;

final class WalletRequestAuthentication {

  private final String apiKey;

  WalletRequestAuthentication(WalletDownstreamProperties properties) {
    this.apiKey = properties.requiredApiKey();
  }

  ServerRequest apply(ServerRequest request) {
    return ServerRequest.from(request)
        .headers(
            headers -> {
              headers.set(GatewayHeaders.INTERNAL_SERVICE, "gateway");
              headers.set(GatewayHeaders.INTERNAL_API_KEY, apiKey);
            })
        .build();
  }
}
