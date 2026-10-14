package com.lowcortisol.backend.adminorder;

import java.time.OffsetDateTime;

public record OrderStatusChangeResponse(long changeId, long orderId, Long adminUserId, String previousStatus,
    String newStatus, long orderVersion, String reason, OffsetDateTime createdAt) {}
