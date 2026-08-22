package com.sportsbook.betting.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.error.ValidationFailedException;
import com.sportsbook.betting.policy.BettingPolicyProperties;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Odds;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetSlipValidatorTest {

  private final BetSlipValidator validator =
      new BetSlipValidator(new BettingPolicyProperties(2, null, null, null, null));

  @Test
  void rejectsRepeatedMarketBeforeExternalCalls() {
    UUID event = UUID.randomUUID();
    UUID market = UUID.randomUUID();
    List<BetLeg> legs =
        List.of(leg(event, market), leg(event, market));

    assertThatThrownBy(() -> validator.validate(new BetSlipType.Multiple(), legs))
        .isInstanceOf(ValidationFailedException.class)
        .hasMessageContaining("Same market");
  }

  @Test
  void rejectsDuplicateSelectionIdentity() {
    UUID selection = UUID.randomUUID();
    List<BetLeg> legs =
        List.of(
            BetLeg.create(UUID.randomUUID(), UUID.randomUUID(), selection, Odds.ofDecimal("2.0")),
            BetLeg.create(UUID.randomUUID(), UUID.randomUUID(), selection, Odds.ofDecimal("3.0")));

    assertThatThrownBy(() -> validator.validate(new BetSlipType.Multiple(), legs))
        .isInstanceOf(ValidationFailedException.class)
        .hasMessageContaining("Duplicate selection");
  }
  private BetLeg leg(UUID event, UUID market) {
    return BetLeg.create(event, market, UUID.randomUUID(), Odds.ofDecimal("2.0"));
  }
}
