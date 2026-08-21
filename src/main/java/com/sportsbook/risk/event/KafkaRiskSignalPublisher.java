package com.sportsbook.risk.event;

import com.sportsbook.protocol.event.RiskLimitType;
import com.sportsbook.protocol.event.RiskLimitViolated;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.pattern.PatternMatch;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Best-effort Kafka publisher for shared risk signal records. */
@Component
public final class KafkaRiskSignalPublisher implements RiskSignalPublisher {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaRiskSignalPublisher.class);

  private final KafkaTemplate<String, byte[]> kafka;
  private final EventTopics topics;

  public KafkaRiskSignalPublisher(KafkaTemplate<String, byte[]> kafka, EventTopics topics) {
    this.kafka = Objects.requireNonNull(kafka, "kafka");
    this.topics = Objects.requireNonNull(topics, "topics");
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
    send(topics.limitViolated(), userId.value().toString(), AvroCodec.encode(event));
  }

  @Override
  public void publishPattern(UserId userId, PatternMatch match, Instant occurredAt) {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(match, "match");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }

  private void send(String topic, String key, byte[] payload) {
    try {
      kafka.send(topic, key, payload);
    } catch (RuntimeException exception) {
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
}
