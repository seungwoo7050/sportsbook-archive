package com.sportsbook.risk.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.pattern.PatternMatch;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.SafeRedisNumber;
import com.sportsbook.risk.service.LimitRejection;
import com.sportsbook.risk.service.RiskCheckOutcome;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RiskPayloadTest {
  private static final UserId USER =
      UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final BetId BET =
      BetId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final SelectionId SELECTION =
      SelectionId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void readsSharedIdentifiersFromTheirCanonicalJsonValues() throws Exception {
    String body =
        """
        {"userId":"%s","betId":"%s","stake":{"amount":100,"currency":"KRW"},
         "selectionIds":["%s"]}
        """
            .formatted(USER.value(), BET.value(), SELECTION.value());

    RiskCheckRequest request = json.readValue(body, RiskCheckRequest.class);

    assertThat(request)
        .isEqualTo(new RiskCheckRequest(USER, BET, Money.krw(100), List.of(SELECTION)));
  }

  @Test
  void rejectsUnsafeStakeAndInvalidSelectionSets() {
    List<RiskCheckRequest> invalid =
        List.of(
            request(Money.krw(0), List.of(SELECTION)),
            request(new Money(SafeRedisNumber.MAX_VALUE + 1, Currency.KRW), List.of(SELECTION)),
            request(Money.krw(1), List.of()),
            request(Money.krw(1), List.of(SELECTION, SELECTION)),
            request(
                Money.krw(1),
                IntStream.range(0, 16)
                    .mapToObj(index -> SelectionId.of(new UUID(0, index + 10)))
                    .toList()));

    assertThat(invalid).allSatisfy(value -> assertThat(validator.validate(value)).isNotEmpty());
  }

  @Test
  void serializesLimitAndPatternDecisionsWithTypedFields() throws Exception {
    var limit = RiskCheckOutcome.rejectedByLimit(LimitRejection.single(Currency.KRW, 1000, 1001));
    var flag = new PatternMatch("RAPID_BETTING", PatternAction.SUSPECT, "threshold reached");

    assertThat(json.writeValueAsString(RiskCheckResponse.from(limit)))
        .contains("\"rejectionReason\":\"SINGLE_BET_MAX_EXCEEDED\"")
        .contains("\"currency\":\"KRW\"")
        .doesNotContain("\"type\"");
    assertThat(RiskCheckResponse.from(RiskCheckOutcome.approved(List.of(flag))).patterns())
        .containsExactly(
            new RiskCheckResponse.PatternFlag(
                "RAPID_BETTING", PatternAction.SUSPECT, "threshold reached"));
  }

  private static RiskCheckRequest request(Money stake, List<SelectionId> selections) {
    return new RiskCheckRequest(USER, BET, stake, selections);
  }
}
