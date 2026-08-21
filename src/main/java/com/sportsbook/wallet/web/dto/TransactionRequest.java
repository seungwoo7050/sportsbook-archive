package com.sportsbook.wallet.web.dto;

import com.sportsbook.protocol.value.Money;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Shared request body for a single positive wallet transfer. */
public record TransactionRequest(@NotNull UUID userId, @NotNull Money amount) {}
