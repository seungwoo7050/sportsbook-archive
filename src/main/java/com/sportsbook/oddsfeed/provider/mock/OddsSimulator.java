package com.sportsbook.oddsfeed.provider.mock;

import com.sportsbook.protocol.value.Odds;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

public final class OddsSimulator {

  static final double NOISE_STDDEV = 0.02;
  static final double MEAN_REVERSION = 0.10;
  static final double MIN_ODDS = 1.01;
  static final double MAX_ODDS = 100.0;

  private OddsSimulator() {}

  public static Odds initialOdds(double impliedProbability) {
    double fair = 1.0 / impliedProbability;
    return Odds.ofDecimal(BigDecimal.valueOf(fair).setScale(Odds.SCALE, RoundingMode.HALF_EVEN));
  }

  public static Odds nextOdds(Odds current, double impliedProbability, Random random) {
    double fair = 1.0 / impliedProbability;
    double currentValue = current.decimal().doubleValue();
    double noisy = currentValue * (1.0 + random.nextGaussian() * NOISE_STDDEV);
    double next = noisy * (1.0 - MEAN_REVERSION) + fair * MEAN_REVERSION;
    double clamped = Math.max(MIN_ODDS, Math.min(MAX_ODDS, next));
    return Odds.ofDecimal(BigDecimal.valueOf(clamped).setScale(Odds.SCALE, RoundingMode.HALF_EVEN));
  }
}
