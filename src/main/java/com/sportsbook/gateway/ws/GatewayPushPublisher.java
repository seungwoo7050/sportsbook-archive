package com.sportsbook.gateway.ws;

import com.sportsbook.protocol.event.OddsChanged;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Hands validated Kafka events to the local STOMP broker. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GatewayPushPublisher {

  private final SimpMessagingTemplate messaging;

  public GatewayPushPublisher(SimpMessagingTemplate messaging) {
    this.messaging = messaging;
  }

  public void publishOdds(OddsChanged event) {
    messaging.convertAndSend("/topic/odds/" + event.getEventId(), OddsUpdate.from(event));
  }
}
