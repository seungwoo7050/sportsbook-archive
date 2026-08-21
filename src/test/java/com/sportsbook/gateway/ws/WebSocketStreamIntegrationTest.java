package com.sportsbook.gateway.ws;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.BetVoided;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.OddsChanged;
import com.sportsbook.protocol.event.SettlementResultAvro;
import com.sportsbook.protocol.event.VoidReason;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;

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

  @Test
  void deliversSettledBetOnlyToOwningUser() throws Exception {
    String owner = UUID.randomUUID().toString();
    String other = UUID.randomUUID().toString();
    BetSettled event = betSettled(owner);
    StompSession ownerSession = connect("/ws/v1/bets", authHeaders(owner));
    StompSession otherSession = connect("/ws/v1/bets", authHeaders(other));
    try {
      BlockingQueue<String> ownerMessages = subscribe(ownerSession, "/user/queue/bets");
      BlockingQueue<String> otherMessages = subscribe(otherSession, "/user/queue/bets");
      awaitListener("gateway-settled-listener");

      publish(topics.betSettled(), event.getEventId(), event);

      assertThat(ownerMessages.poll(5, SECONDS))
          .contains(
              event.getBetId(),
              owner,
              "\"status\":\"SETTLED\"",
              "\"result\":\"WON\"",
              "\"revisionNumber\":0");
      assertThat(ownerMessages.poll(1, SECONDS)).isNull();
      assertThat(otherMessages.poll(1, SECONDS)).isNull();
    } finally {
      ownerSession.disconnect();
      otherSession.disconnect();
    }
  }

  @Test
  void deliversVoidedBetOnlyToOwningUser() throws Exception {
    String owner = UUID.randomUUID().toString();
    String other = UUID.randomUUID().toString();
    BetVoided event = betVoided(owner);
    StompSession ownerSession = connect("/ws/v1/bets", authHeaders(owner));
    StompSession otherSession = connect("/ws/v1/bets", authHeaders(other));
    try {
      BlockingQueue<String> ownerMessages = subscribe(ownerSession, "/user/queue/bets");
      BlockingQueue<String> otherMessages = subscribe(otherSession, "/user/queue/bets");
      awaitListener("gateway-voided-listener");

      publish(topics.betVoided(), event.getEventId(), event);

      assertThat(ownerMessages.poll(5, SECONDS))
          .contains(
              event.getBetId(),
              owner,
              "\"status\":\"VOIDED\"",
              "\"reason\":\"EVENT_POSTPONED\"",
              "\"amount\":{\"amount\":10000,\"currency\":\"KRW\"}",
              "\"revisionNumber\":null");
      assertThat(ownerMessages.poll(1, SECONDS)).isNull();
      assertThat(otherMessages.poll(1, SECONDS)).isNull();
    } finally {
      ownerSession.disconnect();
      otherSession.disconnect();
    }
  }

  @Test
  void deliversResolutionRevisionOnlyToOwningUser() throws Exception {
    String owner = UUID.randomUUID().toString();
    String other = UUID.randomUUID().toString();
    BetResolutionRevised event = betResolutionRevised(owner);
    StompSession ownerSession = connect("/ws/v1/bets", authHeaders(owner));
    StompSession otherSession = connect("/ws/v1/bets", authHeaders(other));
    try {
      BlockingQueue<String> ownerMessages = subscribe(ownerSession, "/user/queue/bets");
      BlockingQueue<String> otherMessages = subscribe(otherSession, "/user/queue/bets");
      awaitListener("gateway-revision-listener");

      publish(topics.betResolutionRevised(), event.getBetId(), event);

      assertThat(ownerMessages.poll(5, SECONDS))
          .contains(
              event.getBetId(),
              owner,
              "\"status\":\"SETTLED\"",
              "\"result\":\"WON\"",
              "\"amount\":{\"amount\":18500,\"currency\":\"KRW\"}",
              "\"revisionId\":\"" + event.getRevisionId() + "\"",
              "\"revisionNumber\":3",
              "\"updatedAt\":\"2026-08-21T00:00:03Z\"");
      assertThat(ownerMessages.poll(1, SECONDS)).isNull();
      assertThat(otherMessages.poll(1, SECONDS)).isNull();
    } finally {
      ownerSession.disconnect();
      otherSession.disconnect();
    }
  }

  protected StompHeaders authHeaders(String userId) {
    when(jwtDecoder.decode(userId))
        .thenReturn(
            Jwt.withTokenValue(userId)
                .header("alg", "RS256")
                .subject(userId)
                .expiresAt(Instant.now().plusSeconds(60))
                .build());
    StompHeaders headers = new StompHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + userId);
    return headers;
  }

  protected void awaitListener(String id) {
    await()
        .atMost(5, SECONDS)
        .until(() -> !listeners.getListenerContainer(id).getAssignedPartitions().isEmpty());
  }

  protected static BetSettled betSettled(String userId) {
    Money stake = Money.newBuilder().setAmount(10_000).setCurrency("KRW").build();
    return BetSettled.newBuilder()
        .setBetId(UUID.randomUUID().toString())
        .setUserId(userId)
        .setEventId(UUID.randomUUID().toString())
        .setResult(SettlementResultAvro.WON)
        .setStake(stake)
        .setPayout(Money.newBuilder(stake).setAmount(18_500).build())
        .setSettledAt(Instant.parse("2026-08-21T00:00:01Z"))
        .build();
  }

  protected static BetVoided betVoided(String userId) {
    return BetVoided.newBuilder()
        .setBetId(UUID.randomUUID().toString())
        .setUserId(userId)
        .setEventId(UUID.randomUUID().toString())
        .setReason(VoidReason.EVENT_POSTPONED)
        .setRefund(Money.newBuilder().setAmount(10_000).setCurrency("KRW").build())
        .setVoidedAt(Instant.parse("2026-08-21T00:00:02Z"))
        .build();
  }

  protected static BetResolutionRevised betResolutionRevised(String userId) {
    return BetResolutionRevised.newBuilder()
        .setRevisionId(UUID.randomUUID().toString())
        .setRevisionNumber(3)
        .setBetId(UUID.randomUUID().toString())
        .setUserId(userId)
        .setEventId(UUID.randomUUID().toString())
        .setPreviousResult(SettlementResultAvro.LOST)
        .setNewResult(SettlementResultAvro.WON)
        .setPreviousPayout(Money.newBuilder().setAmount(0).setCurrency("KRW").build())
        .setNewPayout(Money.newBuilder().setAmount(18_500).setCurrency("KRW").build())
        .setSourceResultSettledAt(Instant.parse("2026-08-21T00:00:00Z"))
        .setRevisedAt(Instant.parse("2026-08-21T00:00:03Z"))
        .build();
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
