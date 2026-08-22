package com.sportsbook.settlement.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetPlacementValidatorTest {

  private final BetPlacementValidator validator = new BetPlacementValidator();

  @Test
  void acceptsMatchingSystemShapeWithPositiveUnitStake() {
    BetPlacement placement =
        placement(new BetSlipType.System(1, 2), Money.krw(100), List.of(selection(), selection()));

    assertThat(validator.validate(placement)).isSameAs(placement);
  }

  @Test
  void rejectsStakeShapeAndDuplicateSelectionViolations() {
    BetPlacement.Selection duplicate = selection();

    assertInvalid(placement(new BetSlipType.Single(), Money.krw(0), List.of(selection())));
    assertInvalid(
        placement(new BetSlipType.System(1, 3), Money.krw(100), List.of(selection(), selection())));
    assertInvalid(
        placement(new BetSlipType.Multiple(), Money.krw(100), List.of(duplicate, duplicate)));
  }

  private void assertInvalid(BetPlacement placement) {
    assertThatThrownBy(() -> validator.validate(placement))
        .isInstanceOf(PlacementContractException.class)
        .hasMessageStartingWith("Invalid BetPlacedRequested:");
  }

  private static BetPlacement placement(
      BetSlipType type, Money stake, List<BetPlacement.Selection> selections) {
    return new BetPlacement(
        UUID.randomUUID(), UUID.randomUUID(), type, stake, Instant.EPOCH, selections);
  }

  private static BetPlacement.Selection selection() {
    return new BetPlacement.Selection(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
  }
}
