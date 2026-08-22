package com.sportsbook.betting.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.protocol.value.Odds;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class OddsSnapshotReaderTest {

  @Test
  void readsCanonicalEffectiveStatusAndPriceKeys() {
    UUID event = UUID.randomUUID();
    UUID market = UUID.randomUUID();
    UUID selection = UUID.randomUUID();
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("market:" + event + ":" + market)).thenReturn("OPEN");
    when(values.get("odds:" + event + ":" + market + ":" + selection)).thenReturn("2.25");

    BetLeg leg = BetLeg.create(event, market, selection, Odds.ofDecimal("2.0"));

    assertThat(new OddsSnapshotReader(redis).currentOdds(leg)).isEqualByComparingTo("2.25");
  }
}
