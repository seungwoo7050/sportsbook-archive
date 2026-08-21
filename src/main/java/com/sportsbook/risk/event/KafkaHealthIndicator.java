package com.sportsbook.risk.event;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/** Reports whether Kafka can answer cluster metadata within the readiness budget. */
@Component
public final class KafkaHealthIndicator implements HealthIndicator {
  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private final Supplier<AdminClient> adminClients;

  @Autowired
  public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
    Objects.requireNonNull(kafkaAdmin, "kafkaAdmin");
    this.adminClients = () -> AdminClient.create(kafkaAdmin.getConfigurationProperties());
  }

  KafkaHealthIndicator(Supplier<AdminClient> adminClients) {
    this.adminClients = Objects.requireNonNull(adminClients, "adminClients");
  }

  @Override
  public Health health() {
    try (AdminClient admin = adminClients.get()) {
      admin.describeCluster().clusterId().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      return Health.up().build();
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      return Health.down(failure).build();
    } catch (Exception failure) {
      return Health.down(failure).build();
    }
  }
}
