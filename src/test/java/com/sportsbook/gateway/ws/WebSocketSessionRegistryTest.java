package com.sportsbook.gateway.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

class WebSocketSessionRegistryTest {

  @Test
  void tracksEstablishedSessionsUntilTransportClose() throws Exception {
    WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
    WebSocketHandler delegate = mock(WebSocketHandler.class);
    WebSocketSession session = session("session-1");
    WebSocketHandler tracked = registry.decorate(delegate);

    tracked.afterConnectionEstablished(session);
    assertThat(registry.size()).isOne();
    verify(delegate).afterConnectionEstablished(session);

    tracked.afterConnectionClosed(session, CloseStatus.NORMAL);
    assertThat(registry.size()).isZero();
    verify(delegate).afterConnectionClosed(session, CloseStatus.NORMAL);
  }

  @Test
  void removesSessionsWhenDelegateLifecycleCallbacksFail() throws Exception {
    WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
    WebSocketHandler delegate = mock(WebSocketHandler.class);
    WebSocketSession session = session("session-2");
    IOException failure = new IOException("delegate failed");
    doThrow(failure).when(delegate).afterConnectionEstablished(session);

    assertThatThrownBy(() -> registry.decorate(delegate).afterConnectionEstablished(session))
        .isSameAs(failure);
    assertThat(registry.size()).isZero();
  }

  @Test
  @SuppressWarnings("unchecked")
  void closesAuthenticatedSessionWithPolicyViolationAtExpiry() throws Exception {
    WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
    WebSocketSession session = session("expiring-session");
    when(session.isOpen()).thenReturn(true);
    registry.decorate(mock(WebSocketHandler.class)).afterConnectionEstablished(session);
    TaskScheduler scheduler = mock(TaskScheduler.class);
    ObjectProvider<TaskScheduler> schedulers = mock(ObjectProvider.class);
    ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
    Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
    when(schedulers.getObject()).thenReturn(scheduler);
    doReturn(scheduled).when(scheduler).schedule(any(Runnable.class), eq(expiresAt));
    AuthenticatedSessionExpiryInterceptor expiry =
        new AuthenticatedSessionExpiryInterceptor(registry, schedulers);

    expiry.preSend(connect("expiring-session", expiresAt, StompCommand.STOMP), null);

    var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).schedule(task.capture(), eq(expiresAt));
    task.getValue().run();
    verify(session).close(CloseStatus.POLICY_VIOLATION);
    assertThat(registry.size()).isZero();
  }

  @Test
  @SuppressWarnings("unchecked")
  void cancelsExpiryWhenTheTransportDisconnectsEarly() {
    TaskScheduler scheduler = mock(TaskScheduler.class);
    ObjectProvider<TaskScheduler> schedulers = mock(ObjectProvider.class);
    ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
    Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
    when(schedulers.getObject()).thenReturn(scheduler);
    doReturn(scheduled).when(scheduler).schedule(any(Runnable.class), eq(expiresAt));
    AuthenticatedSessionExpiryInterceptor expiry =
        new AuthenticatedSessionExpiryInterceptor(new WebSocketSessionRegistry(), schedulers);
    expiry.preSend(connect("disconnected-session", expiresAt, StompCommand.CONNECT), null);
    SessionDisconnectEvent disconnect = mock(SessionDisconnectEvent.class);
    when(disconnect.getSessionId()).thenReturn("disconnected-session");

    expiry.disconnect(disconnect);

    verify(scheduled).cancel(false);
  }

  private static WebSocketSession session(String id) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    return session;
  }

  private static Message<byte[]> connect(
      String sessionId, Instant expiresAt, StompCommand command) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("11111111-1111-4111-8111-111111111111")
            .claim("roles", List.of("USER"))
            .expiresAt(expiresAt)
            .build();
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setSessionId(sessionId);
    accessor.setUser(new JwtAuthenticationToken(jwt));
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
