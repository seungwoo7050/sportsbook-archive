package com.sportsbook.wallet.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdjustmentRequestTest {
  private static final UUID REVISION_ID = UUID.fromString("019b76da-a000-7000-8000-000000000491");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-000000000492");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000493");
  private static final IdempotencyKey KEY = IdempotencyKey.of("settlement:revision:" + REVISION_ID);

  @Test
  void preservesTheExactAdjustmentBodyAndCommandIdentity() throws Exception {
    AdjustmentRequest request = request();
    JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    JsonNode json = mapper.valueToTree(request);
    List<String> fields = new ArrayList<>();
    json.fieldNames().forEachRemaining(fields::add);
    assertThat(fields)
        .containsExactly(
            "revisionId", "betId", "revisionNumber", "userId", "previousPayout", "newPayout");
    assertThat(mapper.treeToValue(json, AdjustmentRequest.class)).isEqualTo(request);
    assertThat(request.toCommand(KEY))
        .isEqualTo(
            new AdjustmentCommand(
                REVISION_ID, BET_ID, 3L, USER_ID, Money.krw(700), Money.krw(1_000), KEY));
  }

  @Test
  void requiresEveryAdjustmentIdentityAndPositiveRevisionNumber() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    assertThat(validator.validate(new AdjustmentRequest(null, null, 0L, null, null, null)))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactlyInAnyOrder(
            "revisionId", "betId", "revisionNumber", "userId", "previousPayout", "newPayout");
    assertThat(validator.validate(request())).isEmpty();
  }

  private AdjustmentRequest request() {
    return new AdjustmentRequest(
        REVISION_ID, BET_ID, 3L, USER_ID, Money.krw(700), Money.krw(1_000));
  }
}
