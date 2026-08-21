package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.kafka.support.Acknowledgment;

class BetPlacedConsumerReconciliationTest {
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
  void successfulReconciliationAcknowledgesTheSourceEvent() {
    when(reconciler.reconcile(any())).thenReturn(AcceptedBetReconciliation.CONFIRMED);

    consumer.onBetPlaced(
        BetPlacedEventFixture.payload(), BetPlacedEventFixture.USER_ID, acknowledgment);

    ArgumentCaptor<AcceptedBetEnvelope> envelope =
        ArgumentCaptor.forClass(AcceptedBetEnvelope.class);
    verify(reconciler).reconcile(envelope.capture());
    assertThat(envelope.getValue().command().now()).isEqualTo(BetPlacedEventFixture.OBSERVED_AT);
    verify(deadLetters, never()).publishAndAwait(any(), any(), any());
    verify(acknowledgment).acknowledge();
  }

  @ParameterizedTest
  @MethodSource("permanentResults")
  void permanentReservationResultsAreDeadLetteredBeforeAcknowledgment(
      AcceptedBetReconciliation result, BetPlacedFailureReason reason) {
    byte[] payload = BetPlacedEventFixture.payload();
    when(reconciler.reconcile(any())).thenReturn(result);

    consumer.onBetPlaced(payload, BetPlacedEventFixture.USER_ID, acknowledgment);

    InOrder order = inOrder(deadLetters, acknowledgment);
    order.verify(deadLetters).publishAndAwait(BetPlacedEventFixture.USER_ID, payload, reason);
    order.verify(acknowledgment).acknowledge();
  }

  @Test
  void transientReconciliationFailureIsRetriedWithoutDeadLetterOrAcknowledgment() {
    when(reconciler.reconcile(any())).thenThrow(new IllegalStateException("redis unavailable"));

    assertThatThrownBy(
            () ->
                consumer.onBetPlaced(
                    BetPlacedEventFixture.payload(), BetPlacedEventFixture.USER_ID, acknowledgment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("redis unavailable");

    verify(deadLetters, never()).publishAndAwait(any(), any(), any());
    verify(acknowledgment, never()).acknowledge();
  }

  private static Stream<Arguments> permanentResults() {
    return Stream.of(
        Arguments.of(
            AcceptedBetReconciliation.FINGERPRINT_MISMATCH,
            BetPlacedFailureReason.FINGERPRINT_MISMATCH),
        Arguments.of(
            AcceptedBetReconciliation.TERMINAL_RESERVATION,
            BetPlacedFailureReason.TERMINAL_RESERVATION));
  }
}
