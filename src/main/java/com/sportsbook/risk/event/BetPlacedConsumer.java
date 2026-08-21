package com.sportsbook.risk.event;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Reconciles accepted bets and durably quarantines only permanent input failures. */
@Component
public final class BetPlacedConsumer {
  private final Supplier<AcceptedBetReconciler> reconciler;
  private final BetPlacedDeadLetterPublisher deadLetters;
  private final Clock clock;

  @Autowired
  public BetPlacedConsumer(
      ObjectProvider<AcceptedBetReconciler> reconciler, BetPlacedDeadLetterPublisher deadLetters) {
    this((Supplier<AcceptedBetReconciler>) reconciler::getIfUnique, deadLetters, Clock.systemUTC());
  }

  BetPlacedConsumer(
      AcceptedBetReconciler reconciler, BetPlacedDeadLetterPublisher deadLetters, Clock clock) {
    this(() -> Objects.requireNonNull(reconciler, "reconciler"), deadLetters, clock);
  }

  private BetPlacedConsumer(
      Supplier<AcceptedBetReconciler> reconciler,
      BetPlacedDeadLetterPublisher deadLetters,
      Clock clock) {
    this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
    this.deadLetters = Objects.requireNonNull(deadLetters, "deadLetters");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @KafkaListener(
      topics = "${risk.topics.bet-placed:bet.placed.v1}",
      groupId = "${spring.kafka.consumer.group-id:risk.bet-placed-consumer}")
  public void onBetPlaced(
      @Payload(required = false) byte[] payload,
      @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
      Acknowledgment acknowledgment) {
    Objects.requireNonNull(acknowledgment, "acknowledgment");
    AcceptedBetEnvelope envelope;
    try {
      envelope = AcceptedBetEnvelope.decode(key, payload, clock.instant());
    } catch (RuntimeException failure) {
      deadLetter(key, payload, BetPlacedFailureReason.fromDecodeFailure(failure), acknowledgment);
      return;
    }

    AcceptedBetReconciliation result =
        Objects.requireNonNull(requiredReconciler().reconcile(envelope), "reconciliation result");
    if (result.permanentFailure()) {
      deadLetter(key, payload, result.failureReason(), acknowledgment);
      return;
    }
    acknowledgment.acknowledge();
  }

  private AcceptedBetReconciler requiredReconciler() {
    AcceptedBetReconciler value = reconciler.get();
    if (value == null) {
      throw new IllegalStateException("exactly one accepted-bet reconciler is required");
    }
    return value;
  }

  private void deadLetter(
      String key, byte[] payload, BetPlacedFailureReason reason, Acknowledgment acknowledgment) {
    deadLetters.publishAndAwait(key, payload, reason);
    acknowledgment.acknowledge();
  }
}
