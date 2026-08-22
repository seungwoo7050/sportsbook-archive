package com.sportsbook.admin.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sportsbook.protocol.value.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record RiskLimitsResponse(UUID userId, List<Entry> limits) {

  private static final Set<Target> REQUIRED_TARGETS =
      Set.of(
          new Target(RiskLimitType.STAKE_DAILY, Currency.KRW),
          new Target(RiskLimitType.STAKE_DAILY, Currency.USD),
          new Target(RiskLimitType.STAKE_WEEKLY, Currency.KRW),
          new Target(RiskLimitType.STAKE_WEEKLY, Currency.USD),
          new Target(RiskLimitType.STAKE_MONTHLY, Currency.KRW),
          new Target(RiskLimitType.STAKE_MONTHLY, Currency.USD),
          new Target(RiskLimitType.SELECTIONS_PER_MINUTE, null));

  public static RiskLimitsResponse verify(UUID requestedUser, RiskLimitsResponse response) {
    if (response == null
        || !requestedUser.equals(response.userId())
        || response.limits() == null
        || response.limits().size() != REQUIRED_TARGETS.size()) {
      throw violation();
    }
    Set<Target> targets = new HashSet<>();
    for (Entry entry : response.limits()) {
      if (!valid(entry) || !targets.add(new Target(entry.type(), entry.currency()))) {
        throw violation();
      }
    }
    if (!targets.equals(REQUIRED_TARGETS)) {
      throw violation();
    }
    return response;
  }

  private static boolean valid(Entry entry) {
    if (entry == null
        || entry.type() == null
        || entry.value() == null
        || entry.source() == null
        || entry.value() < 0
        || entry.value() > RiskLimitPayload.MAX_SAFE_VALUE) {
      return false;
    }
    return entry.type().requiresCurrency() == (entry.currency() != null);
  }

  private static DownstreamContractException violation() {
    return new DownstreamContractException("complete seven-entry Risk limits response");
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Entry(RiskLimitType type, Currency currency, Long value, Source source) {}

  public enum Source {
    POLICY,
    OVERRIDE
  }

  private record Target(RiskLimitType type, Currency currency) {}
}
