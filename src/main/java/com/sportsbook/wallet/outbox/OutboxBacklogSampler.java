package com.sportsbook.wallet.outbox;

import com.sportsbook.wallet.persistence.OutboxDeliveryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Refreshes gauges separately so a monitoring scrape never queries PostgreSQL. */
@Component
@ConditionalOnProperty(name = "wallet.outbox.scheduling-enabled", havingValue = "true")
public class OutboxBacklogSampler {

  private final OutboxDeliveryRepository delivery;
  private final OutboxMetrics metrics;

  public OutboxBacklogSampler(OutboxDeliveryRepository delivery, OutboxMetrics metrics) {
    this.delivery = delivery;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${wallet.outbox.metrics-interval:PT5S}")
  public void sample() {
    metrics.sample(delivery.snapshot());
  }
}
