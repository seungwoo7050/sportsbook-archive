package com.sportsbook.betting.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetDraft;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.domain.VoidReason;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BetResponseResolutionTest {

  private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");

  @Test
  void omitsResolutionBeforeAResult() throws Exception {
    BetResponse response = BetResponse.from(accepted(UUID.randomUUID()));

    assertThat(response.resolution()).isNull();
    assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
        .doesNotContain("\"resolution\"");
  }

  @Test
  void exposesBaseSettlementAsLogicalRevisionZero() {
    UUID eventId = UUID.randomUUID();
    Bet bet = accepted(eventId);
    bet.settleBase(eventId, SettlementResult.WON, Money.krw(1_000), Money.krw(2_000), NOW, hash());

    assertThat(BetResponse.from(bet).resolution())
        .isEqualTo(
            new BetResponse.ResolutionView("WON", Money.krw(2_000), null, NOW, eventId, null, 0L));
  }

  @Test
  void normalizesLegacyTerminalRowsWithoutRevisionProofToZero() {
    UUID eventId = UUID.randomUUID();
    Bet bet = accepted(eventId);
    bet.settleBase(eventId, SettlementResult.WON, Money.krw(1_000), Money.krw(2_000), NOW, hash());
    ReflectionTestUtils.setField(bet, "resolutionRevisionNumber", null);

    assertThat(BetResponse.from(bet).resolution().resolutionRevisionNumber()).isZero();
  }

  @Test
  void exposesTheCurrentCorrectionWhenRevisionArrivesBeforeBase() {
    UUID eventId = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    Bet bet = accepted(eventId);
    bet.applyRevision(
        eventId,
        revisionId,
        1,
        SettlementResult.LOST,
        SettlementResult.WON,
        Money.krw(0),
        Money.krw(2_000),
        NOW,
        NOW.plusSeconds(1),
        hash());

    assertThat(BetResponse.from(bet).resolution())
        .isEqualTo(
            new BetResponse.ResolutionView(
                "WON", Money.krw(2_000), null, NOW.plusSeconds(1), eventId, revisionId, 1L));
  }

  @Test
  void exposesVoidWithoutInventingARefund() {
    UUID eventId = UUID.randomUUID();
    Bet bet = accepted(eventId);
    bet.voidBase(eventId, VoidReason.EVENT_CANCELLED, NOW, hash());

    assertThat(BetResponse.from(bet).resolution())
        .isEqualTo(
            new BetResponse.ResolutionView(null, null, "EVENT_CANCELLED", NOW, eventId, null, 0L));
  }

  private static Bet accepted(UUID eventId) {
    Bet bet =
        Bet.pending(
            new BetDraft(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "B-2026-08-22-00000001",
                new BetSlipType.Single(),
                Money.krw(1_000),
                Money.krw(2_000),
                IdempotencyKey.of("response-resolution"),
                hash(),
                NOW),
            List.of(
                BetLeg.create(eventId, UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2"))));
    bet.recordRiskReservation(NOW.plusSeconds(60), hash(), true, NOW);
    bet.confirmWallet(UUID.randomUUID(), NOW);
    bet.commitRisk(NOW);
    bet.accept(NOW);
    return bet;
  }

  private static String hash() {
    return "a".repeat(64);
  }
}
