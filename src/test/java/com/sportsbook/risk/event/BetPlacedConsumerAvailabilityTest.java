package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.support.Acknowledgment;

class BetPlacedConsumerAvailabilityTest {
  @Test
  void missingReconcilerFailsClosedWithoutAcknowledgment() {
    @SuppressWarnings("unchecked")
    ObjectProvider<AcceptedBetReconciler> provider = mock(ObjectProvider.class);
    BetPlacedDeadLetterPublisher deadLetters = mock(BetPlacedDeadLetterPublisher.class);
    Acknowledgment acknowledgment = mock(Acknowledgment.class);
    when(provider.getIfUnique()).thenReturn(null);
    BetPlacedConsumer consumer = new BetPlacedConsumer(provider, deadLetters);

    assertThatThrownBy(
            () ->
                consumer.onBetPlaced(
                    BetPlacedEventFixture.payload(), BetPlacedEventFixture.USER_ID, acknowledgment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("exactly one accepted-bet reconciler is required");

    verifyNoInteractions(deadLetters, acknowledgment);
  }
}
