package com.sportsbook.risk.event;

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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.apache.avro.specific.SpecificRecordBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Best-effort Kafka publisher for shared risk signal records. */
@Component
public final class KafkaRiskSignalPublisher implements RiskSignalPublisher {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaRiskSignalPublisher.class);

  private final KafkaTemplate<String, byte[]> kafka;
  private final EventTopics topics;
  private final Counter delivered;
  private final Counter failed;

  @Autowired
  public KafkaRiskSignalPublisher(
      KafkaTemplate<String, byte[]> kafka, EventTopics topics, MeterRegistry meters) {
    this.kafka = Objects.requireNonNull(kafka, "kafka");
    this.topics = Objects.requireNonNull(topics, "topics");
    Objects.requireNonNull(meters, "meters");
    this.delivered =
        Counter.builder("risk.signal.delivery").tag("outcome", "delivered").register(meters);
    this.failed = Counter.builder("risk.signal.delivery").tag("outcome", "failed").register(meters);
  }

  KafkaRiskSignalPublisher(KafkaTemplate<String, byte[]> kafka, EventTopics topics) {
    this(kafka, topics, io.micrometer.core.instrument.Metrics.globalRegistry);
  }

  @Override
  public void publishLimit(
      UserId userId,
      LimitType type,
      long current,
      long limit,
      Money candidate,
      Instant occurredAt) {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(candidate, "candidate");
    Objects.requireNonNull(occurredAt, "occurredAt");
    RiskLimitType wireType = wireType(type);
    if (wireType == null) {
      return;
    }
    RiskLimitViolated event =
        new RiskLimitViolated(
            userId.value().toString(),
            wireType,
            current,
            limit,
            new com.sportsbook.protocol.event.Money(
                candidate.amount(), candidate.currency().name()),
            occurredAt);
    send(topics.limitViolated(), userId.value().toString(), event);
  }

  @Override
  public void publishPattern(UserId userId, PatternMatch match, Instant occurredAt) {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(match, "match");
    Objects.requireNonNull(occurredAt, "occurredAt");
    RiskPatternType wireType = patternType(match.rule());
    if (wireType == null) {
      return;
    }
    RiskPatternSuspected event =
        new RiskPatternSuspected(
            userId.value().toString(),
            wireType,
            Map.of("action", match.action().name(), "reason", match.reason()),
            occurredAt);
    send(topics.patternSuspected(), userId.value().toString(), event);
  }

  private void send(String topic, String key, SpecificRecordBase record) {
    try {
      kafka
          .send(topic, key, AvroCodec.encode(record))
          .whenComplete(
              (result, exception) -> {
                if (exception == null) {
                  delivered.increment();
                } else {
                  failed.increment();
                  LOG.warn("risk signal delivery failed topic={}", topic, exception);
                }
              });
    } catch (RuntimeException exception) {
      failed.increment();
      LOG.warn("risk signal submission failed topic={}", topic, exception);
    }
  }

  private static RiskLimitType wireType(LimitType type) {
    return switch (type) {
      case STAKE_DAILY -> RiskLimitType.STAKE_DAILY;
      case SELECTIONS_PER_MINUTE -> RiskLimitType.SELECTIONS_PER_MINUTE;
      case STAKE_WEEKLY, STAKE_MONTHLY -> null;
    };
  }

  private static RiskPatternType patternType(String rule) {
    return switch (rule) {
      case RapidBettingRule.NAME -> RiskPatternType.RAPID_BETTING;
      case SuddenStakeIncreaseRule.NAME -> RiskPatternType.SUDDEN_STAKE_INCREASE;
      case RepeatedSameSelectionRule.NAME -> RiskPatternType.REPEATED_SAME_SELECTION;
      default -> null;
    };
  }
}
