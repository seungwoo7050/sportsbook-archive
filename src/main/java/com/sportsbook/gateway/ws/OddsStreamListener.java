package com.sportsbook.gateway.ws;

import com.sportsbook.gateway.events.GatewayEventContract;
import com.sportsbook.protocol.event.OddsChanged;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Publishes validated odds events to their public event stream. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OddsStreamListener {

  private final GatewayPushPublisher publisher;

  public OddsStreamListener(GatewayPushPublisher publisher) {
    this.publisher = publisher;
  }

  @KafkaListener(
      id = "gateway-odds-listener",
      topics = "${gateway.topics.odds-changed}",
      groupId = "gateway-odds",
      containerFactory = "kafkaListenerContainerFactory",
      autoStartup = "${spring.kafka.listener.auto-startup:true}")
  public void onOddsChanged(ConsumerRecord<byte[], byte[]> record) {
    OddsChanged event = GatewayEventContract.oddsChanged(record);
    publisher.publishOdds(event);
  }
}
