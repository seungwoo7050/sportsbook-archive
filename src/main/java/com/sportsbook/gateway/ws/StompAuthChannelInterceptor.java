package com.sportsbook.gateway.ws;

import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class StompAuthChannelInterceptor implements ChannelInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtDecoder jwtDecoder;

  public StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
    this.jwtDecoder = jwtDecoder;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor != null && isConnect(accessor.getCommand())) {
      authenticate(accessor);
    }
    return message;
  }

  private void authenticate(StompHeaderAccessor accessor) {
    List<String> headers = accessor.getNativeHeader(HttpHeaders.AUTHORIZATION);
    if (headers == null || headers.isEmpty()) {
      accessor.setUser(null);
      return;
    }
    if (headers.size() != 1
        || !headers.get(0).startsWith(BEARER_PREFIX)
        || headers.get(0).length() == BEARER_PREFIX.length()) {
      throw new MessageDeliveryException("Invalid Authorization header");
    }
    try {
      Jwt jwt = jwtDecoder.decode(headers.get(0).substring(BEARER_PREFIX.length()));
      accessor.setUser(new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject()));
    } catch (JwtException failure) {
      throw new MessageDeliveryException("Invalid or expired token");
    }
  }

  private static Collection<GrantedAuthority> authorities(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    return roles == null
        ? List.of()
        : roles.stream()
            .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
  }

  static boolean isConnect(StompCommand command) {
    return StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command);
  }
}
