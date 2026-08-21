package com.sportsbook.wallet.web.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountResponseTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000401");
  private static final Instant OPENED_AT = Instant.parse("2026-08-21T12:00:00Z");

  @Test
  void mapsOnlyThePublicAccountSnapshot() {
    Instant frozenAt = OPENED_AT.plusSeconds(3);
    Account account = Account.openFor(USER_ID, Currency.KRW, OPENED_AT);
    account.increaseAvailable(Money.krw(1_000), OPENED_AT.plusSeconds(1));
    account.moveAvailableToLocked(Money.krw(300), OPENED_AT.plusSeconds(2));
    account.queueRecoveryDebt(Money.krw(100), frozenAt);

    AccountResponse response = AccountResponse.from(account);

    assertThat(response)
        .isEqualTo(
            new AccountResponse(
                USER_ID,
                Currency.KRW,
                Money.krw(700),
                Money.krw(300),
                true,
                0L,
                OPENED_AT,
                frozenAt));
    JsonNode json = JsonMapper.builder().findAndAddModules().build().valueToTree(response);
    List<String> fields = new ArrayList<>();
    json.fieldNames().forEachRemaining(fields::add);
    assertThat(fields)
        .containsExactly(
            "userId",
            "currency",
            "available",
            "locked",
            "outboundFrozen",
            "version",
            "createdAt",
            "updatedAt");
    assertThat(json.at("/available/amount").longValue()).isEqualTo(700L);
    assertThat(json.at("/locked/amount").longValue()).isEqualTo(300L);
  }

  @Test
  void validatesOpenAccountFieldsAndNullMapping() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    assertThat(validator.validate(new OpenAccountRequest(null, null)))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactlyInAnyOrder("userId", "currency");
    assertThat(validator.validate(new OpenAccountRequest(USER_ID, Currency.USD))).isEmpty();
    assertThatNullPointerException().isThrownBy(() -> AccountResponse.from(null));
  }
}
