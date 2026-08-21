package com.sportsbook.gateway.ws;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.sportsbook.gateway.events.AvroTestSupport;
import com.sportsbook.gateway.kafka.GatewayTopicProperties;
import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "gateway.ratelimit.enabled=false",
      "gateway.downstream.wallet.api-key=fixture-wallet-key-32-characters-long",
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "spring.kafka.listener.auto-startup=true"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = {
      "odds.changed",
      "odds.changed.DLT",
      "bet.settled.v1",
      "bet.settled.v1.DLT",
      "bet.voided.v1",
      "bet.voided.v1.DLT",
      "bet.resolution.revised.v1",
      "bet.resolution.revised.v1.DLT"
    },
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(SubscriptionRegistrationProbe.class)
abstract class WebSocketStreamFixture {

  @LocalServerPort protected int port;
  @Autowired protected KafkaTemplate<byte[], byte[]> kafka;
  @Autowired protected GatewayTopicProperties topics;
  @Autowired protected SubscriptionRegistrationProbe registrations;
  @MockBean protected JwtDecoder jwtDecoder;

  protected StompSession connect(String path, StompHeaders headers) throws Exception {
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    return client
        .connectAsync(
            "ws://localhost:" + port + path,
            new WebSocketHttpHeaders(),
            headers,
            new StompSessionHandlerAdapter() {})
        .get(5, SECONDS);
  }

  protected BlockingQueue<String> subscribe(StompSession session, String destination)
      throws Exception {
    BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    String subscriptionId = UUID.randomUUID().toString();
    StompHeaders headers = new StompHeaders();
    headers.setDestination(destination);
    headers.setId(subscriptionId);
    CompletableFuture<Void> registered = registrations.expect(subscriptionId);
    try {
      session.subscribe(
          headers,
          new StompFrameHandler() {
            public Type getPayloadType(StompHeaders ignored) {
              return byte[].class;
            }

            public void handleFrame(StompHeaders ignored, Object payload) {
              messages.add(new String((byte[]) payload, UTF_8));
            }
          });
      registered.get(5, SECONDS);
      return messages;
    } finally {
      registrations.release(subscriptionId);
    }
  }

  protected void publish(String topic, String key, SpecificRecord event) throws Exception {
    kafka.send(topic, key.getBytes(UTF_8), AvroTestSupport.encode(event)).get(5, SECONDS);
  }
}
