package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class KafkaHealthIndicatorTest {
  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void reportsUpAfterBrokerMetadataConfirmation() throws Exception {
    Fixture fixture = fixture();
    when(fixture.clusterId().get(2_000, TimeUnit.MILLISECONDS)).thenReturn("cluster");

    assertThat(fixture.indicator().health().getStatus()).isEqualTo(Status.UP);
    verify(fixture.admin()).close();
  }

  @Test
  void reportsDownWhenBrokerMetadataFails() throws Exception {
    Fixture fixture = fixture();
    when(fixture.clusterId().get(2_000, TimeUnit.MILLISECONDS))
        .thenThrow(new ExecutionException(new IllegalStateException("unavailable")));

    assertThat(fixture.indicator().health().getStatus()).isEqualTo(Status.DOWN);
    verify(fixture.admin()).close();
  }

  @Test
  void restoresInterruptionBeforeReportingDown() throws Exception {
    Fixture fixture = fixture();
    when(fixture.clusterId().get(2_000, TimeUnit.MILLISECONDS))
        .thenThrow(new InterruptedException());

    assertThat(fixture.indicator().health().getStatus()).isEqualTo(Status.DOWN);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  private static Fixture fixture() {
    AdminClient admin = mock(AdminClient.class);
    DescribeClusterResult cluster = mock(DescribeClusterResult.class);
    @SuppressWarnings("unchecked")
    KafkaFuture<String> clusterId = mock(KafkaFuture.class);
    when(admin.describeCluster()).thenReturn(cluster);
    when(cluster.clusterId()).thenReturn(clusterId);
    return new Fixture(new KafkaHealthIndicator(() -> admin), admin, clusterId);
  }

  private record Fixture(
      KafkaHealthIndicator indicator, AdminClient admin, KafkaFuture<String> clusterId) {}
}
