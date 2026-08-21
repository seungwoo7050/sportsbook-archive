package com.sportsbook.wallet.outbox;

import com.sportsbook.wallet.persistence.OutboxEventRepository;
import com.sportsbook.wallet.persistence.OutboxStreamLock;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Appends one immutable event at a stream position owned by the caller's transaction. */
@Component
public class OutboxAppender {
  private final OutboxStreamLock streams;
  private final OutboxEventRepository events;

  public OutboxAppender(OutboxStreamLock streams, OutboxEventRepository events) {
    this.streams = Objects.requireNonNull(streams, "streams");
    this.events = Objects.requireNonNull(events, "events");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public OutboxEvent append(PendingOutboxMessage message) {
    Objects.requireNonNull(message, "message");
    long sequence = streams.nextSequence(message.topic(), message.partitionKey());
    return events.save(OutboxEvent.pending(message, sequence));
  }
}
