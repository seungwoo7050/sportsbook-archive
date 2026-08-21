package com.sportsbook.gateway.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private static final int MESSAGE_SIZE_LIMIT = 64 * 1024;
  private static final int SEND_BUFFER_LIMIT = 512 * 1024;
  private static final int SEND_TIME_LIMIT = 10_000;

  private final String[] allowedOrigins;
  private final StompAuthChannelInterceptor authentication;
  private final WebSocketSessionRegistry sessions;

  public WebSocketConfig(
      @Value("${gateway.ws.allowed-origins}") String[] allowedOrigins,
      StompAuthChannelInterceptor authentication,
      WebSocketSessionRegistry sessions) {
    this.allowedOrigins = allowedOrigins;
    this.authentication = authentication;
    this.sessions = sessions;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws/v1/odds", "/ws/v1/bets").setAllowedOriginPatterns(allowedOrigins);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authentication);
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
        .setMessageSizeLimit(MESSAGE_SIZE_LIMIT)
        .setSendBufferSizeLimit(SEND_BUFFER_LIMIT)
        .setSendTimeLimit(SEND_TIME_LIMIT)
        .addDecoratorFactory(sessions);
  }
}
