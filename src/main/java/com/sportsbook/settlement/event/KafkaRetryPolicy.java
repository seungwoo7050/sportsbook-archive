package com.sportsbook.settlement.event;

import com.sportsbook.settlement.event.MessageFailureClassifier.Disposition;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

/** Bounded listener retry policy; permanent contracts have no retry cycle. */
@ConfigurationProperties("settlement.kafka.retry")
public record KafkaRetryPolicy(int maxAttempts, Duration backoff, Duration dltSendTimeout) {

  public KafkaRetryPolicy {
    maxAttempts = maxAttempts == 0 ? 3 : maxAttempts;
    backoff = backoff == null ? Duration.ofSeconds(1) : backoff;
    dltSendTimeout = dltSendTimeout == null ? Duration.ofSeconds(11) : dltSendTimeout;
    if (maxAttempts < 1
        || maxAttempts > 10
        || backoff.isNegative()
        || dltSendTimeout.isZero()
        || dltSendTimeout.isNegative()) {
      throw new IllegalArgumentException("Invalid settlement Kafka retry policy");
    }
  }

  public BackOff backOffFor(Throwable failure, MessageFailureClassifier classifier) {
    if (classifier.classify(failure) == Disposition.PERMANENT) {
      return new FixedBackOff(0, 0);
    }
    return new FixedBackOff(backoff.toMillis(), maxAttempts - 1L);
  }
}
