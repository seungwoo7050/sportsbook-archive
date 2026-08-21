package com.sportsbook.gateway.routing;

import com.sportsbook.gateway.security.GatewayHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

@Component
public final class DownstreamRequestSanitizer {

  public ServerRequest apply(ServerRequest request) {
    return ServerRequest.from(request)
        .headers(
            headers -> {
              headers.remove(HttpHeaders.AUTHORIZATION);
              headers.remove(GatewayHeaders.USER_ID);
              headers.remove(GatewayHeaders.USER_ROLES);
              headers.remove(GatewayHeaders.INTERNAL_SERVICE);
              headers.remove(GatewayHeaders.INTERNAL_API_KEY);
            })
        .build();
  }
}
