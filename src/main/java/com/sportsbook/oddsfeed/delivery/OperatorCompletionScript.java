package com.sportsbook.oddsfeed.delivery;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/** Atomic Redis transition applied after Kafka acknowledges an operator action. */
final class OperatorCompletionScript {

  static final RedisScript<String> INSTANCE =
      new DefaultRedisScript<>(
          """
          local sequence = tonumber(ARGV[1])
          local predecessor = tonumber(ARGV[2])
          local committed = tonumber(redis.call('GET', KEYS[1]) or '0')
          local tail = tonumber(redis.call('GET', KEYS[2]) or '0')
          local provider = redis.call('GET', KEYS[4]) or 'OPEN'
          local terminal = redis.call('EXISTS', KEYS[7]) == 1
            or redis.call('EXISTS', KEYS[8]) == 1
          if provider == 'CLOSED' then
            redis.call('SET', KEYS[8], 'MARKET_CLOSED', 'NX')
            terminal = true
          end
          if committed >= sequence then
            local idempotency = redis.call('GET', KEYS[6])
            redis.call('PEXPIRE', KEYS[6], ARGV[5])
            if idempotency then
              redis.call('PEXPIRE', idempotency, ARGV[5])
            end
            if tail == committed then
              redis.call('PEXPIRE', KEYS[1], ARGV[5])
              redis.call('PEXPIRE', KEYS[2], ARGV[5])
            end
            return 'COMPLETED'
          end
          if committed ~= predecessor then
            return 'BLOCKED'
          end

          redis.call('SET', KEYS[1], tostring(sequence))
          local idempotency = redis.call('GET', KEYS[6])
          redis.call('PEXPIRE', KEYS[6], ARGV[5])
          if idempotency then
            redis.call('PEXPIRE', idempotency, ARGV[5])
          end
          if tail ~= sequence then
            return 'SUPERSEDED'
          end

          local requested = ARGV[3]
          local effective = requested
          if terminal then
            effective = 'CLOSED'
          elseif requested == 'OPEN' then
            effective = redis.call('GET', KEYS[3]) or provider
          else
            redis.call('SET', KEYS[5], requested)
          end
          redis.call('PSETEX', KEYS[3], ARGV[4], effective)
          redis.call('HSET', KEYS[10], ARGV[6], effective)
          redis.call('PEXPIRE', KEYS[10], ARGV[4])
          redis.call('PEXPIRE', KEYS[1], ARGV[5])
          redis.call('PEXPIRE', KEYS[2], ARGV[5])
          return 'APPLIED'
          """,
          String.class);

  private OperatorCompletionScript() {}
}
