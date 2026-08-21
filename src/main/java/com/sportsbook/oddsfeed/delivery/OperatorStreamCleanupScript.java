package com.sportsbook.oddsfeed.delivery;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/** Atomically removes a delivered action from its consumer group and Stream. */
final class OperatorStreamCleanupScript {

  static final RedisScript<String> INSTANCE =
      new DefaultRedisScript<>(
          """
          local acknowledged = redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
          local deleted = redis.call('XDEL', KEYS[1], ARGV[2])
          return tostring(acknowledged) .. '|' .. tostring(deleted)
          """,
          String.class);

  private OperatorStreamCleanupScript() {}
}
