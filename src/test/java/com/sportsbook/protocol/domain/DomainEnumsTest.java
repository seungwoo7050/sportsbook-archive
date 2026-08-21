package com.sportsbook.protocol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Pins ADR-0013 value sets. Any addition/removal forces a test update and surfaces the change in
 * code review — preventing silent vocabulary drift across services.
 */
class DomainEnumsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void marketTypeV1SetIsExactlyFourMarkets() {
    assertThat(MarketType.values())
        .containsExactlyInAnyOrder(
            MarketType.MATCH_RESULT_1X2,
            MarketType.TOTAL_OVER_UNDER,
            MarketType.BOTH_TEAMS_TO_SCORE,
            MarketType.DOUBLE_CHANCE);
  }

  @Test
  void betStatusV1SetCoversFullLifecycle() {
    assertThat(BetStatus.values())
        .containsExactlyInAnyOrder(
            BetStatus.PENDING,
            BetStatus.ACCEPTED,
            BetStatus.REJECTED,
            BetStatus.SETTLED,
            BetStatus.CANCELLED,
            BetStatus.VOIDED);
  }

  @Test
  void settlementResultV1SetExcludesHalfWonAndCashedOut() {
    assertThat(SettlementResult.values())
        .containsExactlyInAnyOrder(
            SettlementResult.WON,
            SettlementResult.LOST,
            SettlementResult.PUSH,
            SettlementResult.VOID);
  }

  @Test
  void marketTypeValueOfRoundTrips() {
    assertThat(MarketType.valueOf("MATCH_RESULT_1X2")).isEqualTo(MarketType.MATCH_RESULT_1X2);
    assertThat(MarketType.valueOf("DOUBLE_CHANCE")).isEqualTo(MarketType.DOUBLE_CHANCE);
  }

  @Test
  void betStatusValueOfRoundTrips() {
    assertThat(BetStatus.valueOf("PENDING")).isEqualTo(BetStatus.PENDING);
    assertThat(BetStatus.valueOf("VOIDED")).isEqualTo(BetStatus.VOIDED);
  }

  @Test
  void settlementResultValueOfRoundTrips() {
    assertThat(SettlementResult.valueOf("WON")).isEqualTo(SettlementResult.WON);
    assertThat(SettlementResult.valueOf("PUSH")).isEqualTo(SettlementResult.PUSH);
  }

  @Test
  void marketTypeJsonSerializesAsEnumName() throws Exception {
    assertThat(mapper.writeValueAsString(MarketType.MATCH_RESULT_1X2))
        .isEqualTo("\"MATCH_RESULT_1X2\"");
    assertThat(mapper.readValue("\"DOUBLE_CHANCE\"", MarketType.class))
        .isEqualTo(MarketType.DOUBLE_CHANCE);
  }

  @Test
  void betStatusJsonRoundTrips() throws Exception {
    String json = mapper.writeValueAsString(BetStatus.ACCEPTED);
    assertThat(mapper.readValue(json, BetStatus.class)).isEqualTo(BetStatus.ACCEPTED);
  }

  @Test
  void settlementResultJsonRoundTrips() throws Exception {
    String json = mapper.writeValueAsString(SettlementResult.VOID);
    assertThat(mapper.readValue(json, SettlementResult.class)).isEqualTo(SettlementResult.VOID);
  }
}
