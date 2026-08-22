package com.sportsbook.admin.api;

import java.util.UUID;

public record RefundResponse(UUID operationGroupId, UUID actionId) {}
