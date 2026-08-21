package com.sportsbook.wallet.web.dto;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.CreditReason;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request body for a caller-authorized wallet credit. */
public record CreditRequest(
    @NotNull UUID userId,
    @NotNull Money amount,
    @NotNull CreditCommand.Source source,
    @NotNull CreditReason reason) {}
