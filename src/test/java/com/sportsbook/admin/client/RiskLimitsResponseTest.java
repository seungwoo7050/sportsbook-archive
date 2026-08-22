package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.admin.client.RiskLimitsResponse.Entry;
import com.sportsbook.admin.client.RiskLimitsResponse.Source;
import com.sportsbook.protocol.value.Currency;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskLimitsResponseTest {

  private static final UUID USER = UUID.fromString("018f0000-0000-7000-8000-000000000121");

  @Test
  void acceptsTheSevenUniqueSupportedTargets() {
    RiskLimitsResponse response = new RiskLimitsResponse(USER, validEntries());

    assertThat(RiskLimitsResponse.verify(USER, response)).isSameAs(response);
  }

  @Test
  void rejectsMissingDuplicateMismatchedAndUnsafeEntries() {
    UUID other = UUID.fromString("018f0000-0000-7000-8000-000000000122");
    List<RiskLimitsResponse> invalid = new ArrayList<>();
    invalid.add(new RiskLimitsResponse(other, validEntries()));
    invalid.add(new RiskLimitsResponse(USER, validEntries().subList(0, 6)));

    List<Entry> duplicate = new ArrayList<>(validEntries());
    duplicate.set(6, duplicate.get(0));
    invalid.add(new RiskLimitsResponse(USER, duplicate));

    List<Entry> wrongScope = new ArrayList<>(validEntries());
    wrongScope.set(
        6, new Entry(RiskLimitType.SELECTIONS_PER_MINUTE, Currency.KRW, 20L, Source.POLICY));
    invalid.add(new RiskLimitsResponse(USER, wrongScope));

    List<Entry> unsafe = new ArrayList<>(validEntries());
    unsafe.set(0, new Entry(RiskLimitType.STAKE_DAILY, Currency.KRW, -1L, Source.POLICY));
    invalid.add(new RiskLimitsResponse(USER, unsafe));

    invalid.forEach(
        response ->
            assertThatThrownBy(() -> RiskLimitsResponse.verify(USER, response))
                .isInstanceOf(DownstreamContractException.class));
  }

  private static List<Entry> validEntries() {
    return List.of(
        entry(RiskLimitType.STAKE_DAILY, Currency.KRW, 1_000L),
        entry(RiskLimitType.STAKE_DAILY, Currency.USD, 100L),
        entry(RiskLimitType.STAKE_WEEKLY, Currency.KRW, 5_000L),
        entry(RiskLimitType.STAKE_WEEKLY, Currency.USD, 500L),
        entry(RiskLimitType.STAKE_MONTHLY, Currency.KRW, 20_000L),
        entry(RiskLimitType.STAKE_MONTHLY, Currency.USD, 2_000L),
        entry(RiskLimitType.SELECTIONS_PER_MINUTE, null, 20L));
  }

  private static Entry entry(RiskLimitType type, Currency currency, long value) {
    return new Entry(type, currency, value, Source.POLICY);
  }
}
