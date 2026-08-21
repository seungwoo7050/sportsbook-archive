package com.sportsbook.gateway.ws;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthenticatedSessionExpiryInterceptor implements ChannelInterceptor {

  private final WebSocketSessionRegistry sessions;
  private final ObjectProvider<TaskScheduler> scheduler;
  private final ConcurrentMap<String, Future<?>> expiryTasks = new ConcurrentHashMap<>();

  public AuthenticatedSessionExpiryInterceptor(
      WebSocketSessionRegistry sessions,
      @Qualifier("messageBrokerTaskScheduler") ObjectProvider<TaskScheduler> scheduler) {
    this.sessions = sessions;
    this.scheduler = scheduler;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    if (StompAuthChannelInterceptor.isConnect(accessor.getCommand())
        && accessor.getUser() instanceof JwtAuthenticationToken authentication) {
      schedule(accessor.getSessionId(), authentication.getToken().getExpiresAt());
    }
    return message;
  }

  @EventListener
  void disconnect(SessionDisconnectEvent event) {
    Future<?> expiry = expiryTasks.remove(event.getSessionId());
    if (expiry != null) {
      expiry.cancel(false);
    }
  }

  private void schedule(String sessionId, Instant expiresAt) {
    if (sessionId == null || expiresAt == null) {
      throw new MessageDeliveryException("Authenticated session expiry is unavailable");
    }
    FutureTask<Void> expiry = new FutureTask<>(() -> expire(sessionId));
    if (expiryTasks.putIfAbsent(sessionId, expiry) != null) {
      throw new MessageDeliveryException("WebSocket session expiry is unavailable");
    }
    ScheduledFuture<?> scheduled = null;
    try {
      scheduled = scheduler.getObject().schedule(expiry, expiresAt);
      if (scheduled == null || !expiryTasks.replace(sessionId, expiry, scheduled)) {
        throw new IllegalStateException("expiry task was not registered");
      }
    } catch (RuntimeException failure) {
      expiryTasks.remove(sessionId, expiry);
      expiry.cancel(false);
      if (scheduled != null) {
        scheduled.cancel(false);
      }
      throw new MessageDeliveryException(null, "WebSocket expiry could not be scheduled", failure);
    }
  }

  private Void expire(String sessionId) throws IOException {
    if (expiryTasks.remove(sessionId) != null) {
      sessions.closeExpired(sessionId);
    }
    return null;
  }
}
