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

          local requested = ARGV[5]
          local provider = redis.call('GET', KEYS[4]) or 'OPEN'
          local terminal = redis.call('EXISTS', KEYS[7]) == 1
            or redis.call('EXISTS', KEYS[8]) == 1
          if provider == 'CLOSED' then
            redis.call('SET', KEYS[8], 'MARKET_CLOSED', 'NX')
            terminal = true
          end

          local previous = redis.call('GET', KEYS[3]) or 'OPEN'
          local announced = requested
          if terminal then
            previous = 'CLOSED'
            announced = 'CLOSED'
          elseif requested == 'OPEN' then
            announced = provider
          end

          if requested ~= 'OPEN' then
            redis.call('SET', KEYS[5], requested)
            redis.call('PSETEX', KEYS[3], ARGV[8], announced)
            redis.call('HSET', KEYS[9], ARGV[4], announced)
          else
            redis.call('HSETNX', KEYS[9], ARGV[4], previous)
          end
          redis.call('PEXPIRE', KEYS[9], ARGV[8])

          local record = redis.call(
            'XADD', KEYS[10], '*',
            'fingerprint', ARGV[1],
            'actionId', ARGV[2],
            'eventId', ARGV[3],
            'marketId', ARGV[4],
            'requestedStatus', requested,
            'previousStatus', previous,
            'announcedStatus', announced,
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
