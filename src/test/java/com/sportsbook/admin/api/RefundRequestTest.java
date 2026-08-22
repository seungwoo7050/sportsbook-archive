package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class RefundRequestTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void normalizesTheAuditReasonAndBuildsMoney() {
    RefundRequest request = new RefundRequest(750, Currency.KRW, "  goodwill refund  ");

    assertThat(validator.validate(request)).isEmpty();
    assertThat(request.reason()).isEqualTo("goodwill refund");
    assertThat(request.money()).isEqualTo(Money.krw(750));
  }

  @Test
  void rejectsNonPositiveMoneyAndInvalidReasons() {
    assertThat(validator.validate(new RefundRequest(0, Currency.KRW, "refund")))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("amount");
    assertThat(validator.validate(new RefundRequest(1, null, "refund")))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("currency");
    assertThat(validator.validate(new RefundRequest(1, Currency.KRW, "   ")))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("reason");
    assertThat(validator.validate(new RefundRequest(1, Currency.KRW, "r".repeat(257))))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("reason");
  }
}
