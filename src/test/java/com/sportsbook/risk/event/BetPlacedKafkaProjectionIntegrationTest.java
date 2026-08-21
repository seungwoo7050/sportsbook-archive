package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BetPlacedKafkaProjectionIntegrationTest extends BetPlacedKafkaIntegrationSupport {
  @Test
  void binaryAcceptedBetReachesTheReconciler() throws Exception {
    when(reconciler.reconcile(any())).thenReturn(AcceptedBetReconciliation.PROJECTED);

    publish(BetPlacedEventFixture.USER_ID, BetPlacedEventFixture.payload());

    ArgumentCaptor<AcceptedBetEnvelope> envelope =
        ArgumentCaptor.forClass(AcceptedBetEnvelope.class);
    verify(reconciler, timeout(10_000)).reconcile(envelope.capture());
    assertThat(envelope.getValue().command().userId().value().toString())
        .isEqualTo(BetPlacedEventFixture.USER_ID);
    assertThat(envelope.getValue().command().stake().amount()).isEqualTo(10_000L);
  }

  @Test
  void transientReconciliationFailureRedeliversTheSameEvent() throws Exception {
    when(reconciler.reconcile(any()))
        .thenThrow(new IllegalStateException("redis unavailable"))
        .thenReturn(AcceptedBetReconciliation.PROJECTED);

    publish(BetPlacedEventFixture.USER_ID, BetPlacedEventFixture.payload());

    verify(reconciler, timeout(10_000).times(2)).reconcile(any());
  }
}
