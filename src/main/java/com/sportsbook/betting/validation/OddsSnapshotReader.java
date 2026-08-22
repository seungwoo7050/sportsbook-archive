package com.sportsbook.betting.validation;

import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.error.MarketClosedException;
import java.math.BigDecimal;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OddsSnapshotReader {

  private final StringRedisTemplate redis;

  public OddsSnapshotReader(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public BigDecimal currentOdds(BetLeg leg) {
    String marketKey = "market:" + leg.eventId() + ":" + leg.marketId();
    String status = redis.opsForValue().get(marketKey);
    if (!"OPEN".equals(status)) {
      throw new MarketClosedException("Market is not effectively OPEN");
    }

    String oddsKey = "odds:" + leg.eventId() + ":" + leg.marketId() + ":" + leg.selectionId();
    String value = redis.opsForValue().get(oddsKey);
    if (value == null) {
      throw new MarketClosedException("Selection is no longer priced");
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      throw new MarketClosedException("Selection price is invalid");
    }
  }
}
