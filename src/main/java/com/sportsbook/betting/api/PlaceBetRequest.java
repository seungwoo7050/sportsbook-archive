package com.sportsbook.betting.api;

import com.sportsbook.protocol.value.Money;
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

  public record SlipTypeRequest(@NotBlank String type, Integer minWins, Integer totalSelections) {}

  public record SelectionRequest(
      @NotNull UUID eventId,
      @NotNull UUID marketId,
      @NotNull UUID selectionId,
      @NotNull BigDecimal odds) {}
}
