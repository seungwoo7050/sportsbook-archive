package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

  @Test
  void isStableButSeparatesSystemShape() {
    UUID user = UUID.randomUUID();
    var selection =
        new PlaceBetCommand.SelectionInput(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.5000"));
    PlaceBetCommand first = command(user, new BetSlipType.System(2, 3), selection);
    PlaceBetCommand same = command(user, new BetSlipType.System(2, 3), selection);
    PlaceBetCommand different = command(user, new BetSlipType.System(1, 3), selection);

    assertThat(RequestFingerprint.of(first)).matches("[0-9a-f]{64}");
    assertThat(RequestFingerprint.of(first)).isEqualTo(RequestFingerprint.of(same));
    assertThat(RequestFingerprint.of(first)).isNotEqualTo(RequestFingerprint.of(different));
  }

  private PlaceBetCommand command(
      UUID user, BetSlipType type, PlaceBetCommand.SelectionInput input) {
    return new PlaceBetCommand(
        user, type, List.of(input), Money.krw(1_000), IdempotencyKey.of("key"));
  }
}
