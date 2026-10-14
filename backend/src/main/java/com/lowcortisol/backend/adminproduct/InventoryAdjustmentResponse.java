package com.lowcortisol.backend.adminproduct;

import java.time.OffsetDateTime;

public record InventoryAdjustmentResponse(long adjustmentId, long productId, Long adminUserId,
    int quantityBefore, int quantityAfter, long delta, String reason, OffsetDateTime createdAt, Long orderId) {}
