package com.sportsbook.gateway.ws;

import com.sportsbook.gateway.events.GatewayEventContract;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.BetVoided;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Publishes validated terminal bet events to their owning user. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BetStatusStreamListener {

  private final GatewayPushPublisher publisher;

  public BetStatusStreamListener(GatewayPushPublisher publisher) {
    this.publisher = publisher;
  }

  @KafkaListener(
      id = "gateway-settled-listener",
      topics = "${gateway.topics.bet-settled}",
      groupId = "gateway-bets",
      containerFactory = "kafkaListenerContainerFactory",
      autoStartup = "${spring.kafka.listener.auto-startup:true}")
  public void onBetSettled(ConsumerRecord<byte[], byte[]> record) {
    BetSettled event = GatewayEventContract.betSettled(record);
    publisher.publishBet(event.getUserId(), BetStatusUpdate.settled(event));
  }

  @KafkaListener(
      id = "gateway-voided-listener",
      topics = "${gateway.topics.bet-voided}",
      groupId = "gateway-bets",
      containerFactory = "kafkaListenerContainerFactory",
      autoStartup = "${spring.kafka.listener.auto-startup:true}")
  public void onBetVoided(ConsumerRecord<byte[], byte[]> record) {
    BetVoided event = GatewayEventContract.betVoided(record);
    publisher.publishBet(event.getUserId(), BetStatusUpdate.voided(event));
  }
}
