package com.sportsbook.gateway.routing;

import com.sportsbook.gateway.security.GatewayHeaders;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

@Component
public final class IdentityForwarding {

  public ServerRequest apply(ServerRequest request) {
    Jwt jwt = currentJwt();
    if (jwt == null) {
      return request;
    }
    ServerRequest.Builder forwarded =
        ServerRequest.from(request).header(GatewayHeaders.USER_ID, jwt.getSubject());
    List<String> roles = jwt.getClaimAsStringList("roles");
    if (roles != null && !roles.isEmpty()) {
      forwarded.header(GatewayHeaders.USER_ROLES, String.join(",", roles));
    }
    return forwarded.build();
  }

  public Optional<String> currentSubject() {
    Jwt jwt = currentJwt();
    return jwt == null ? Optional.empty() : Optional.of(jwt.getSubject());
  }

  private static Jwt currentJwt() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof Jwt jwt) {
      return jwt;
    }
    return null;
  }
}
