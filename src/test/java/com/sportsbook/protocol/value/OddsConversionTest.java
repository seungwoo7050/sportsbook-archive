package com.sportsbook.protocol.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OddsConversionTest {

  @Test
  void favoritesConvertToNegativeAmericanOdds() {
    assertThat(Odds.ofDecimal("1.5").toAmerican()).isEqualTo("-200");
    assertThat(Odds.ofDecimal("1.85").toAmerican()).isEqualTo("-118");
  }

  @Test
  void underdogsConvertToPositiveAmericanOdds() {
    assertThat(Odds.ofDecimal("2.0").toAmerican()).isEqualTo("+100");
    assertThat(Odds.ofDecimal("2.5").toAmerican()).isEqualTo("+150");
  }

  @Test
  void fractionalOddsAreReduced() {
    assertThat(Odds.ofDecimal("1.85").toFractional()).isEqualTo("17/20");
    assertThat(Odds.ofDecimal("3.0").toFractional()).isEqualTo("2/1");
  }

  @Test
  void AmericanOddsConvertToNormalizedDecimals() {
    assertThat(Odds.ofAmerican(150).decimal()).isEqualTo(new BigDecimal("2.5000"));
    assertThat(Odds.ofAmerican(-200).decimal()).isEqualTo(new BigDecimal("1.5000"));
  }

  @Test
  void AmericanOddsRejectInvalidMagnitude() {
    assertThatIllegalArgumentException().isThrownBy(() -> Odds.ofAmerican(99));
    assertThatIllegalArgumentException().isThrownBy(() -> Odds.ofAmerican(-99));
  }
}
