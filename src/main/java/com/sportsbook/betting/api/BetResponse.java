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
    ResolutionView resolution,
    Instant createdAt) {

  public record SlipTypeView(String type, Integer minWins, Integer totalSelections) {}

  public record SelectionView(
      UUID eventId, UUID marketId, UUID selectionId, String oddsAtSubmission) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ResolutionView(
      String settlementResult,
      Money settledPayout,
      String voidReason,
      Instant resolvedAt,
      UUID resolutionEventId,
      UUID resolutionRevisionId,
      Long resolutionRevisionNumber) {}

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
    ResolutionView resolution =
        bet.resolvedAt() == null
            ? null
            : new ResolutionView(
                bet.settlementResult() == null ? null : bet.settlementResult().name(),
                bet.settledPayout(),
                bet.voidReason() == null ? null : bet.voidReason().name(),
                bet.resolvedAt(),
                bet.resolutionEventId(),
                bet.resolutionRevisionId(),
                Math.max(0L, bet.resolutionRevisionNumber()));
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
        resolution,
        bet.createdAt());
  }
}
