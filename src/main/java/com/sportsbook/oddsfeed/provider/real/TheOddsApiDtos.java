package com.sportsbook.oddsfeed.provider.real;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TheOddsApiDtos {

  private TheOddsApiDtos() {}

  @JsonNaming(SnakeCaseStrategy.class)
  public record Event(
      String id,
      String sportKey,
      String sportTitle,
      Instant commenceTime,
      String homeTeam,
      String awayTeam,
      List<Bookmaker> bookmakers) {}

  @JsonNaming(SnakeCaseStrategy.class)
  public record Bookmaker(String key, String title, Instant lastUpdate, List<Market> markets) {}

  @JsonNaming(SnakeCaseStrategy.class)
  public record Market(String key, Instant lastUpdate, List<Outcome> outcomes) {}

  @JsonNaming(SnakeCaseStrategy.class)
  public record Outcome(String name, BigDecimal price) {}
}
