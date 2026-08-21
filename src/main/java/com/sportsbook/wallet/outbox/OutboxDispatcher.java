package com.sportsbook.wallet.outbox;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface OutboxDispatcher {

  CompletionStage<Void> dispatch(LeasedOutboxMessage message);
}
