package com.sportsbook.betting.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletOperationResponse(
    UUID operationGroupId, UUID userId, Money amount, String reason, Instant at) {}
