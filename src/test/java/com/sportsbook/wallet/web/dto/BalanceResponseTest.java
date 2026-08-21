package com.sportsbook.wallet.web.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BalanceResponseTest {
  @Test
  void mapsOnlySpendableAndLockedBalances() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000402");
    Instant now = Instant.parse("2026-08-21T13:00:00Z");
    Account account = Account.openFor(userId, Currency.USD, now);
    account.increaseAvailable(Money.usd(800), now.plusSeconds(1));
    account.moveAvailableToLocked(Money.usd(200), now.plusSeconds(2));
    account.queueRecoveryDebt(Money.usd(100), now.plusSeconds(3));

    BalanceResponse response = BalanceResponse.from(account);

    assertThat(response)
        .isEqualTo(
            new BalanceResponse(userId, Money.usd(600), Money.usd(200), Money.usd(800), true));
    JsonNode json = JsonMapper.builder().findAndAddModules().build().valueToTree(response);
    List<String> fields = new ArrayList<>();
    json.fieldNames().forEachRemaining(fields::add);
    assertThat(fields).containsExactly("userId", "available", "locked", "total", "outboundFrozen");
    assertThat(json.at("/total/amount").longValue()).isEqualTo(800L);
    assertThat(json.at("/total/currency").textValue()).isEqualTo("USD");
  }

  @Test
  void rejectsMissingAccountSnapshots() {
    assertThatNullPointerException().isThrownBy(() -> BalanceResponse.from(null));
  }
}
