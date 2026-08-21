package com.sportsbook.wallet.web.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.service.WalletOperationResult;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionRepresentationTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000421");

  @Test
  void mapsTheAuthoritativeOperationResult() {
    UUID groupId = UUID.fromString("019b76da-a000-7000-8000-000000000422");
    Instant at = Instant.parse("2026-08-21T16:00:00Z");
    WalletOperationResult result =
        new WalletOperationResult(groupId, USER_ID, Money.krw(500), LedgerReason.DEPOSIT, at);

    WalletOperationResponse response = WalletOperationResponse.from(result);

    assertThat(response)
        .isEqualTo(
            new WalletOperationResponse(
                groupId, USER_ID, Money.krw(500), LedgerReason.DEPOSIT, at));
    JsonNode json = JsonMapper.builder().findAndAddModules().build().valueToTree(response);
    List<String> fields = new ArrayList<>();
    json.fieldNames().forEachRemaining(fields::add);
    assertThat(fields).containsExactly("operationGroupId", "userId", "amount", "reason", "at");
    assertThat(json.at("/amount/amount").longValue()).isEqualTo(500L);
    assertThat(json.at("/reason").textValue()).isEqualTo("DEPOSIT");
    assertThatNullPointerException().isThrownBy(() -> WalletOperationResponse.from(null));
  }

  @Test
  void requiresEveryTransactionBodyField() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    assertThat(validator.validate(new TransactionRequest(null, null)))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactlyInAnyOrder("userId", "amount");
    assertThat(validator.validate(new TransactionRequest(USER_ID, Money.usd(1)))).isEmpty();
  }
}
