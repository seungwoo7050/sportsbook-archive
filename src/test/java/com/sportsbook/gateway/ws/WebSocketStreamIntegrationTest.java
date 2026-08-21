package com.sportsbook.gateway.ws;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sportsbook.protocol.event.OddsChanged;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.support.MessageBuilder;

class WebSocketStreamIntegrationTest extends WebSocketStreamFixture {

  @Autowired SimpleBrokerMessageHandler broker;
  @Autowired KafkaListenerEndpointRegistry listeners;

  @Test
  void broadcastsKafkaOddsToEverySubscribedSessionExactlyOnce() throws Exception {
    OddsChanged event = oddsChanged(UUID.randomUUID().toString());
    StompSession first = connect("/ws/v1/odds", new StompHeaders());
    StompSession second = connect("/ws/v1/odds", new StompHeaders());
    try {
      String destination = "/topic/odds/" + event.getEventId();
      BlockingQueue<String> firstMessages = subscribe(first, destination);
      BlockingQueue<String> secondMessages = subscribe(second, destination);
      await()
          .atMost(5, SECONDS)
          .until(
              () ->
                  broker
                          .getSubscriptionRegistry()
                          .findSubscriptions(
                              MessageBuilder.withPayload(new byte[0])
                                  .setHeader(
                                      SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER,
                                      SimpMessageType.MESSAGE)
                                  .setHeader(
                                      SimpMessageHeaderAccessor.DESTINATION_HEADER, destination)
                                  .build())
                          .size()
                      == 2);
      await()
          .atMost(5, SECONDS)
          .until(
              () ->
                  !listeners
                      .getListenerContainer("gateway-odds-listener")
                      .getAssignedPartitions()
                      .isEmpty());

      publish(topics.oddsChanged(), event.getEventId(), event);

      String payload = firstMessages.poll(5, SECONDS);
      assertThat(payload).contains(event.getEventId(), event.getMarketId(), "1.8500", "1.9000");
      assertThat(secondMessages.poll(5, SECONDS)).isEqualTo(payload);
      assertThat(firstMessages.poll(1, SECONDS)).isNull();
      assertThat(secondMessages.poll(1, SECONDS)).isNull();
    } finally {
      first.disconnect();
      second.disconnect();
    }
  }

  protected static OddsChanged oddsChanged(String eventId) {
    return OddsChanged.newBuilder()
        .setEventId(eventId)
        .setMarketId(UUID.randomUUID().toString())
        .setSelectionId(UUID.randomUUID().toString())
        .setPreviousOdds("1.8500")
        .setNewOdds("1.9000")
        .setChangedAt(Instant.parse("2026-08-21T00:00:00Z"))
        .build();
  }
}
