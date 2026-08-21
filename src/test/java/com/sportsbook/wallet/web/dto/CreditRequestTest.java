package com.sportsbook.wallet.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.CreditReason;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreditRequestTest {
  @Test
  void preservesTheCreditSourceAndReasonShape() throws Exception {
    CreditRequest request =
        new CreditRequest(
            UUID.fromString("019b76da-a000-7000-8000-000000000461"),
            Money.krw(400),
            CreditCommand.Source.USER_LOCKED,
            CreditReason.REFUND);
    JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    JsonNode json = mapper.valueToTree(request);

    List<String> fields = new ArrayList<>();
    json.fieldNames().forEachRemaining(fields::add);
    assertThat(fields).containsExactly("userId", "amount", "source", "reason");
    assertThat(json.at("/source").textValue()).isEqualTo("USER_LOCKED");
    assertThat(json.at("/reason").textValue()).isEqualTo("REFUND");
    assertThat(CreditCommand.Source.values())
        .containsExactly(CreditCommand.Source.USER_LOCKED, CreditCommand.Source.HOUSE_POOL);
    assertThat(mapper.treeToValue(json, CreditRequest.class)).isEqualTo(request);
  }

  @Test
  void requiresEveryCreditRequestField() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    assertThat(validator.validate(new CreditRequest(null, null, null, null)))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactlyInAnyOrder("userId", "amount", "source", "reason");
    assertThat(
            validator.validate(
                new CreditRequest(
                    UUID.fromString("019b76da-a000-7000-8000-000000000462"),
                    Money.usd(1),
                    CreditCommand.Source.HOUSE_POOL,
                    CreditReason.PAYOUT)))
        .isEmpty();
  }
}
