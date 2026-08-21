package com.sportsbook.risk.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.kafka.support.Acknowledgment;

class BetPlacedConsumerInvalidInputTest {
  private final AcceptedBetReconciler reconciler = mock(AcceptedBetReconciler.class);
  private final BetPlacedDeadLetterPublisher deadLetters = mock(BetPlacedDeadLetterPublisher.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private BetPlacedConsumer consumer;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(BetPlacedEventFixture.OBSERVED_AT, ZoneOffset.UTC);
    consumer = new BetPlacedConsumer(reconciler, deadLetters, clock);
  }

  @Test
  void malformedPayloadIsPublishedBeforeItsSourceOffsetIsAcknowledged() {
    byte[] malformed = {1, 2, 3};

    consumer.onBetPlaced(malformed, BetPlacedEventFixture.USER_ID, acknowledgment);

    InOrder order = inOrder(deadLetters, acknowledgment);
    order
        .verify(deadLetters)
        .publishAndAwait(
            eq(BetPlacedEventFixture.USER_ID),
            same(malformed),
            eq(BetPlacedFailureReason.MALFORMED_EVENT));
    order.verify(acknowledgment).acknowledge();
    verify(reconciler, never()).reconcile(any());
  }

  @Test
  void mismatchedKafkaKeyHasItsOwnPermanentClassification() {
    byte[] payload = BetPlacedEventFixture.payload();

    consumer.onBetPlaced(payload, BetPlacedEventFixture.OTHER_USER_ID, acknowledgment);

    verify(deadLetters)
        .publishAndAwait(
            BetPlacedEventFixture.OTHER_USER_ID, payload, BetPlacedFailureReason.KEY_MISMATCH);
    verify(acknowledgment).acknowledge();
    verify(reconciler, never()).reconcile(any());
  }
}
