package com.sportsbook.wallet.web.dto;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Settlement payout revision body whose key is supplied by the authenticated HTTP request. */
public record AdjustmentRequest(
    @NotNull UUID revisionId,
    @NotNull UUID betId,
    @Min(1) long revisionNumber,
    @NotNull UUID userId,
    @NotNull Money previousPayout,
    @NotNull Money newPayout) {

  public AdjustmentCommand toCommand(IdempotencyKey idempotencyKey) {
    return new AdjustmentCommand(
        revisionId, betId, revisionNumber, userId, previousPayout, newPayout, idempotencyKey);
  }
}
