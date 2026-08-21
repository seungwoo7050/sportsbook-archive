package com.sportsbook.oddsfeed.provider.real;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TheOddsApiDtosTest {

  private static final String PAYLOAD =
      """
      {
        "id": "abc123",
        "sport_key": "soccer_epl",
        "sport_title": "EPL",
        "commence_time": "2026-06-01T18:00:00Z",
        "home_team": "Manchester United",
        "away_team": "Chelsea",
        "bookmakers": [{
          "key": "book",
          "title": "Book",
          "last_update": "2026-05-28T10:00:00Z",
          "markets": [{
            "key": "h2h",
            "last_update": "2026-05-28T10:00:00Z",
            "outcomes": [{"name": "Chelsea", "price": 4.20}]
          }]
        }]
      }
      """;

  @Test
  void mapsSnakeCaseEventAndMarketFields() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    TheOddsApiDtos.Event event = mapper.readValue(PAYLOAD, TheOddsApiDtos.Event.class);

    assertThat(event.id()).isEqualTo("abc123");
    assertThat(event.sportKey()).isEqualTo("soccer_epl");
    assertThat(event.commenceTime()).isEqualTo(Instant.parse("2026-06-01T18:00:00Z"));
    assertThat(event.homeTeam()).isEqualTo("Manchester United");
    var bookmaker = event.bookmakers().get(0);
    assertThat(bookmaker.lastUpdate()).isEqualTo(Instant.parse("2026-05-28T10:00:00Z"));
    assertThat(bookmaker.markets().get(0).outcomes().get(0).price())
        .isEqualByComparingTo(new BigDecimal("4.20"));
  }
}
