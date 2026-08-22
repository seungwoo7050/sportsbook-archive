package com.sportsbook.admin.client;

import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;

public record WalletOperationResponse(
    UUID operationGroupId, UUID userId, Money amount, String reason, Instant at) {}
