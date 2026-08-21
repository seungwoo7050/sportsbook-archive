package com.sportsbook.oddsfeed.delivery;

import org.springframework.data.redis.connection.stream.RecordId;

/** A decoded operator action and its Stream delivery identity. */
public record QueuedOperatorMarketAction(
    RecordId recordId, OperatorMarketAction action, boolean reclaimed) {}
