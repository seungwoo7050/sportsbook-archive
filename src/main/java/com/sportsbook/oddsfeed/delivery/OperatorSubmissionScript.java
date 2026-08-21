package com.sportsbook.oddsfeed.delivery;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/** Atomic Redis transition used when an operator action is accepted. */
final class OperatorSubmissionScript {

  static final RedisScript<String> INSTANCE =
      new DefaultRedisScript<>(
          """
          local existing = redis.call('GET', KEYS[1])
          if existing then
            if string.sub(existing, 1, 64) == ARGV[1] then
              return 'REPLAY|' .. string.sub(existing, 66)
            end
            return 'CONFLICT'
          end
          if redis.call('EXISTS', KEYS[2]) == 1 then
            return 'CONFLICT'
          end

          local record = redis.call(
            'XADD', KEYS[3], '*',
            'fingerprint', ARGV[1],
            'actionId', ARGV[2],
            'eventId', ARGV[3],
            'marketId', ARGV[4],
            'requestedStatus', ARGV[5],
            'previousStatus', 'OPEN',
            'announcedStatus', ARGV[5],
            'reason', ARGV[6],
            'occurredAt', ARGV[7],
            'sequence', '0',
            'predecessor', '-1')
          local metadata = ARGV[1] .. '|' .. ARGV[2] .. '|0|-1|' .. record
          redis.call('SET', KEYS[1], metadata)
          redis.call('SET', KEYS[2], KEYS[1])
          return 'CREATED|' .. ARGV[2] .. '|0|-1|' .. record
          """,
          String.class);

  private OperatorSubmissionScript() {}
}
