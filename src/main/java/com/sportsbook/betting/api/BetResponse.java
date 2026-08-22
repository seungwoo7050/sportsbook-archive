package com.sportsbook.betting.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BetResponse(
    UUID betId,
    String betReference,
    UUID userId,
    SlipTypeView slipType,
    String status,
    Money stake,
    Money maxPayout,
    List<SelectionView> selections,
    String rejectionReason,
    Instant createdAt) {

  public record SlipTypeView(String type, Integer minWins, Integer totalSelections) {}

  public record SelectionView(
      UUID eventId, UUID marketId, UUID selectionId, String oddsAtSubmission) {}

  public static BetResponse from(Bet bet) {
    BetSlipType type = bet.slipType();
    SlipTypeView slip =
        type instanceof BetSlipType.System system
            ? new SlipTypeView("SYSTEM", system.minWins(), system.totalSelections())
            : new SlipTypeView(
                type instanceof BetSlipType.Single ? "SINGLE" : "MULTIPLE", null, null);
    List<SelectionView> selections =
        bet.legs().stream()
            .map(
                leg ->
                    new SelectionView(
                        leg.eventId(),
                        leg.marketId(),
                        leg.selectionId(),
                        leg.oddsAtSubmission().decimal().toPlainString()))
            .toList();
    return new BetResponse(
        bet.betId(),
        bet.betReference(),
        bet.userId(),
        slip,
        bet.status().name(),
        bet.stake(),
        bet.maxPayout(),
        selections,
        bet.rejectionReason(),
        bet.createdAt());
  }
}
