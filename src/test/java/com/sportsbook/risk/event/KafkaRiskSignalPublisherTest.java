package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.event.RiskLimitType;
import com.sportsbook.protocol.event.RiskLimitViolated;
import com.sportsbook.protocol.event.RiskPatternSuspected;
import com.sportsbook.protocol.event.RiskPatternType;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.pattern.PatternMatch;
import com.sportsbook.risk.pattern.rule.RapidBettingRule;
import com.sportsbook.risk.pattern.rule.RepeatedSameSelectionRule;
import com.sportsbook.risk.pattern.rule.SuddenStakeIncreaseRule;
import com.sportsbook.risk.policy.PatternAction;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaRiskSignalPublisherTest {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final EventTopics TOPICS = new EventTopics(null, null, null, null);

  @Test
  void publishesDailyViolationsWithTheActualCandidateMoney() {
    KafkaTemplate<String, byte[]> kafka = template();
    KafkaRiskSignalPublisher publisher = new KafkaRiskSignalPublisher(kafka, TOPICS);
    ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);

    publisher.publishLimit(USER, LimitType.STAKE_DAILY, 900, 1000, Money.usd(150), Instant.EPOCH);

    verify(kafka).send(eq(TOPICS.limitViolated()), eq(USER.value().toString()), payload.capture());
    RiskLimitViolated event = AvroCodec.decode(payload.getValue(), RiskLimitViolated.class);
    assertThat(event.getLimitType()).isEqualTo(RiskLimitType.STAKE_DAILY);
    assertThat(event.getCurrentValue()).isEqualTo(900);
    assertThat(event.getRequestedAmount().getAmount()).isEqualTo(150);
    assertThat(event.getRequestedAmount().getCurrency()).isEqualTo("USD");
  }

  @Test
  void mapsSelectionCountsWithoutInventingCurrencylessMoney() {
    KafkaTemplate<String, byte[]> kafka = template();
    KafkaRiskSignalPublisher publisher = new KafkaRiskSignalPublisher(kafka, TOPICS);
    ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);

    publisher.publishLimit(
        USER, LimitType.SELECTIONS_PER_MINUTE, 28, 30, Money.krw(5000), Instant.EPOCH);

    verify(kafka).send(eq(TOPICS.limitViolated()), eq(USER.value().toString()), payload.capture());
    RiskLimitViolated event = AvroCodec.decode(payload.getValue(), RiskLimitViolated.class);
    assertThat(event.getLimitType()).isEqualTo(RiskLimitType.SELECTIONS_PER_MINUTE);
    assertThat(event.getRequestedAmount().getAmount()).isEqualTo(5000);
    assertThat(event.getRequestedAmount().getCurrency()).isEqualTo("KRW");
    publisher.publishLimit(USER, LimitType.STAKE_WEEKLY, 1, 2, Money.krw(1), Instant.EPOCH);
    verify(kafka, times(1)).send(any(), any(), any());
  }

  @Test
  void containsSynchronousAndAsynchronousDeliveryFailures() {
    KafkaTemplate<String, byte[]> synchronousKafka = template();
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    KafkaRiskSignalPublisher synchronousPublisher =
        new KafkaRiskSignalPublisher(synchronousKafka, TOPICS, meters);
    doThrow(new IllegalStateException("unavailable"))
        .when(synchronousKafka)
        .send(any(), any(), any());

    assertThatCode(
            () ->
                synchronousPublisher.publishLimit(
                    USER, LimitType.STAKE_DAILY, 1, 2, Money.krw(1), Instant.EPOCH))
        .doesNotThrowAnyException();
    CompletableFuture<org.springframework.kafka.support.SendResult<String, byte[]>> failed =
        new CompletableFuture<>();
    failed.completeExceptionally(new IllegalStateException("broker rejected"));
    KafkaTemplate<String, byte[]> asynchronousKafka = template();
    doReturn(failed).when(asynchronousKafka).send(any(), any(), any());
    KafkaRiskSignalPublisher asynchronousPublisher =
        new KafkaRiskSignalPublisher(asynchronousKafka, TOPICS, meters);
    asynchronousPublisher.publishLimit(
        USER, LimitType.STAKE_DAILY, 1, 2, Money.krw(1), Instant.EPOCH);

    assertThat(meters.get("risk.signal.delivery").tag("outcome", "failed").counter().count())
        .isEqualTo(2);
  }

  @Test
  void mapsSupportedPatternSignalsWithStableEvidence() {
    KafkaTemplate<String, byte[]> kafka = template();
    KafkaRiskSignalPublisher publisher = new KafkaRiskSignalPublisher(kafka, TOPICS);
    ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);

    publisher.publishPattern(
        USER,
        new PatternMatch(RapidBettingRule.NAME, PatternAction.SUSPECT, "rapid"),
        Instant.EPOCH);
    publisher.publishPattern(
        USER,
        new PatternMatch(SuddenStakeIncreaseRule.NAME, PatternAction.BLOCK, "sudden"),
        Instant.EPOCH);
    publisher.publishPattern(
        USER,
        new PatternMatch(RepeatedSameSelectionRule.NAME, PatternAction.SUSPECT, "repeated"),
        Instant.EPOCH);

    verify(kafka, times(3))
        .send(eq(TOPICS.patternSuspected()), eq(USER.value().toString()), payload.capture());
    assertThat(payload.getAllValues())
        .extracting(value -> AvroCodec.decode(value, RiskPatternSuspected.class))
        .extracting(RiskPatternSuspected::getPatternType)
        .containsExactly(
            RiskPatternType.RAPID_BETTING,
            RiskPatternType.SUDDEN_STAKE_INCREASE,
            RiskPatternType.REPEATED_SAME_SELECTION);
    RiskPatternSuspected blocked =
        AvroCodec.decode(payload.getAllValues().get(1), RiskPatternSuspected.class);
    assertThat(blocked.getEvidence())
        .containsEntry("action", "BLOCK")
        .containsEntry("reason", "sudden");
  }

  @Test
  void skipsUnknownPatternSignals() {
    KafkaTemplate<String, byte[]> kafka = template();
    KafkaRiskSignalPublisher publisher = new KafkaRiskSignalPublisher(kafka, TOPICS);

    publisher.publishPattern(
        USER, new PatternMatch("UNKNOWN", PatternAction.SUSPECT, "unknown"), Instant.EPOCH);

    verify(kafka, times(0)).send(any(), any(), any());
  }

  @SuppressWarnings("unchecked")
  private static KafkaTemplate<String, byte[]> template() {
    KafkaTemplate<String, byte[]> kafka = mock(KafkaTemplate.class);
    when(kafka.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    return kafka;
  }
}
