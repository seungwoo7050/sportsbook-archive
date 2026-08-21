package com.sportsbook.wallet.web.dto;

import com.sportsbook.protocol.value.Currency;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request body for opening an empty wallet account. */
public record OpenAccountRequest(@NotNull UUID userId, @NotNull Currency currency) {}
