package com.sportsbook.oddsfeed.delivery;

import org.springframework.data.redis.connection.stream.RecordId;

public record QueuedCriticalEvent(RecordId recordId, CriticalEvent event, boolean reclaimed) {}
