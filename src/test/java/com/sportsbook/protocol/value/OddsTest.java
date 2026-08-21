package com.sportsbook.protocol.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OddsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void decimalFactoryNormalizesScale() {
    assertThat(Odds.ofDecimal("1.85").decimal()).isEqualTo(new BigDecimal("1.8500"));
    assertThat(Odds.ofDecimal(new BigDecimal("3")).decimal()).isEqualTo(new BigDecimal("3.0000"));
  }

  @Test
  void decimalOddsCannotFallBelowOne() {
    assertThatIllegalArgumentException().isThrownBy(() -> Odds.ofDecimal("0.99"));
    assertThat(Odds.ofDecimal("1.00").decimal()).isEqualTo(new BigDecimal("1.0000"));
  }

  @Test
  void equalityIgnoresBigDecimalScale() {
    Odds compact = new Odds(new BigDecimal("1.85"));
    Odds padded = new Odds(new BigDecimal("1.8500"));
    assertThat(compact).isEqualTo(padded).hasSameHashCodeAs(padded);
  }

  @Test
  void jsonRoundTripSurvivesScaleChanges() throws Exception {
    Odds original = Odds.ofDecimal("1.85");
    assertThat(mapper.readValue(mapper.writeValueAsString(original), Odds.class))
        .isEqualTo(original);
  }
}
