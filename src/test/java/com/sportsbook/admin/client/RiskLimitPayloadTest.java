package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.Currency;
import org.junit.jupiter.api.Test;

class RiskLimitPayloadTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void acceptsEveryValidScopeAndSafeBoundary() {
    assertThatCode(() -> new RiskLimitPayload(RiskLimitType.STAKE_DAILY, Currency.KRW, 0L))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                new RiskLimitPayload(
                    RiskLimitType.STAKE_MONTHLY, Currency.USD, RiskLimitPayload.MAX_SAFE_VALUE))
        .doesNotThrowAnyException();
    assertThatCode(() -> new RiskLimitPayload(RiskLimitType.SELECTIONS_PER_MINUTE, null, 20L))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsMismatchedScopesAndUnsafeValues() {
    assertThatThrownBy(() -> new RiskLimitPayload(RiskLimitType.STAKE_WEEKLY, null, 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new RiskLimitPayload(RiskLimitType.SELECTIONS_PER_MINUTE, Currency.KRW, 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiskLimitPayload(RiskLimitType.STAKE_DAILY, Currency.KRW, -1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new RiskLimitPayload(
                    RiskLimitType.STAKE_DAILY, Currency.KRW, RiskLimitPayload.MAX_SAFE_VALUE + 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void omitsCurrencyForSelectionLimits() throws Exception {
    var payload = new RiskLimitPayload(RiskLimitType.SELECTIONS_PER_MINUTE, null, 20L);

    assertThat(json.readTree(json.writeValueAsBytes(payload)))
        .isEqualTo(json.readTree("{\"type\":\"SELECTIONS_PER_MINUTE\",\"value\":20}"));
  }
}
