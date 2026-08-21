package com.sportsbook.gateway.ws;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

@Component
public class WebSocketSessionRegistry implements WebSocketHandlerDecoratorFactory {

  private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  @Override
  public WebSocketHandler decorate(WebSocketHandler handler) {
    return new WebSocketHandlerDecorator(handler) {
      @Override
      public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        try {
          super.afterConnectionEstablished(session);
        } catch (Exception failure) {
          sessions.remove(session.getId(), session);
          throw failure;
        }
      }

      @Override
      public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
          throws Exception {
        try {
          super.afterConnectionClosed(session, status);
        } finally {
          sessions.remove(session.getId(), session);
        }
      }
    };
  }

  int size() {
    return sessions.size();
  }
}
