package com.sportsbook.gateway.ws;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@TestConfiguration(proxyBeanMethods = false)
final class SubscriptionRegistrationProbe
    implements ExecutorChannelInterceptor, WebSocketMessageBrokerConfigurer {

  private final ConcurrentMap<String, CompletableFuture<Void>> expectations =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

  CompletableFuture<Void> expect(String subscriptionId) {
    CompletableFuture<Void> expected = new CompletableFuture<>();
    if (expectations.putIfAbsent(subscriptionId, expected) != null) {
      throw new IllegalStateException("duplicate subscription expectation");
    }
    return expected;
  }

  void release(String subscriptionId) {
    expectations.remove(subscriptionId);
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(this);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.configureBrokerChannel().interceptors(this);
  }

  @Override
  public void afterMessageHandled(
      Message<?> message, MessageChannel channel, MessageHandler handler, Exception failure) {
    if (!(handler instanceof SimpleBrokerMessageHandler broker)) {
      return;
    }
    String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
    SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
    if (type == SimpMessageType.DISCONNECT) {
      clearSession(sessionId);
      return;
    }
    if (failure != null || type != SimpMessageType.SUBSCRIBE || !broker.isRunning()) {
      return;
    }
    String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());
    CompletableFuture<Void> expected = expectations.get(subscriptionId);
    if (sessionId == null || expected == null) {
      return;
    }
    sessionSubscriptions
        .computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet())
        .add(subscriptionId);
    String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
    if (destination != null
        && broker.getDestinationPrefixes().stream().anyMatch(destination::startsWith)) {
      expected.complete(null);
    }
  }

  private void clearSession(String sessionId) {
    Set<String> subscriptions = sessionId == null ? null : sessionSubscriptions.remove(sessionId);
    if (subscriptions != null) {
      subscriptions.forEach(this::failPendingExpectation);
    }
  }

  private void failPendingExpectation(String subscriptionId) {
    CompletableFuture<Void> expected = expectations.remove(subscriptionId);
    if (expected != null) {
      expected.completeExceptionally(new IllegalStateException("session disconnected"));
    }
  }
}
