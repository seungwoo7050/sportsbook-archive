package com.sportsbook.betting.api;

import com.sportsbook.betting.error.ValidationFailedException;
import com.sportsbook.betting.placement.PlaceBetCommand;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PlaceBetRequest(
    @NotNull @Valid SlipTypeRequest slipType,
    @NotEmpty @Valid List<SelectionRequest> selections,
    @NotNull Money stake) {

  PlaceBetCommand toCommand(UUID actorId, IdempotencyKey key) {
    BetSlipType type =
        switch (slipType.type()) {
          case "SINGLE" -> {
            requireNoSystemShape();
            yield new BetSlipType.Single();
          }
          case "MULTIPLE" -> {
            requireNoSystemShape();
            yield new BetSlipType.Multiple();
          }
          case "SYSTEM" -> {
            if (slipType.minWins() == null || slipType.totalSelections() == null) {
              throw new ValidationFailedException("SYSTEM shape is incomplete");
            }
            yield new BetSlipType.System(slipType.minWins(), slipType.totalSelections());
          }
          default -> throw new ValidationFailedException("Unknown slip type");
        };
    List<PlaceBetCommand.SelectionInput> inputs =
        selections.stream()
            .map(
                item ->
                    new PlaceBetCommand.SelectionInput(
                        item.eventId(),
                        item.marketId(),
                        item.selectionId(),
                        Odds.ofDecimal(item.odds())))
            .toList();
    return new PlaceBetCommand(actorId, type, inputs, stake, key);
  }

  private void requireNoSystemShape() {
    if (slipType.minWins() != null || slipType.totalSelections() != null) {
      throw new ValidationFailedException("Non-SYSTEM shape must omit system fields");
    }
  }

  public record SlipTypeRequest(@NotBlank String type, Integer minWins, Integer totalSelections) {}

  public record SelectionRequest(
      @NotNull UUID eventId,
      @NotNull UUID marketId,
      @NotNull UUID selectionId,
      @NotNull BigDecimal odds) {}
}
