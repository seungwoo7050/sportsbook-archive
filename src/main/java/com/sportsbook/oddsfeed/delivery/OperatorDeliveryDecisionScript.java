package com.sportsbook.oddsfeed.delivery;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/** Atomically resolves whether and how a queued operator action may be published. */
final class OperatorDeliveryDecisionScript {

  static final RedisScript<String> INSTANCE =
      new DefaultRedisScript<>(
          """
          local sequence = tonumber(ARGV[1])
          local predecessor = tonumber(ARGV[2])
          local committed = tonumber(redis.call('GET', KEYS[1]) or '0')
          if committed >= sequence then
            return 'COMPLETED'
          end
          if committed ~= predecessor then
            return 'BLOCKED'
          end

          local tail = tonumber(redis.call('GET', KEYS[2]) or '0')
          local provider = redis.call('GET', KEYS[3]) or 'OPEN'
          local terminal = redis.call('EXISTS', KEYS[4]) == 1
            or redis.call('EXISTS', KEYS[5]) == 1
          if provider == 'CLOSED' then
            redis.call('SET', KEYS[5], 'MARKET_CLOSED', 'NX')
            terminal = true
          end
          if tail ~= sequence or (ARGV[3] == 'OPEN' and terminal) then
            return 'SKIP'
          end

          local announced = ARGV[3]
          if terminal then
            announced = 'CLOSED'
          elseif announced == 'OPEN' then
            if redis.call('EXISTS', KEYS[6]) == 1 then
              announced = 'SUSPENDED'
            else
              announced = provider
            end
          end
          return 'PUBLISH|' .. announced
          """,
          String.class);

  private OperatorDeliveryDecisionScript() {}
}
