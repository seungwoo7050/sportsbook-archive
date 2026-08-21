package com.sportsbook.gateway.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

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

  private static WebSocketSession session(String id) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    return session;
  }
}
