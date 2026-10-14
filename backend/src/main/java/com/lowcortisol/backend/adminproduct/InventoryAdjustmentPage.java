package com.lowcortisol.backend.adminproduct;

import java.util.List;

public record InventoryAdjustmentPage(List<InventoryAdjustmentResponse> items, int page, int pageSize,
    long totalItems, long totalPages) {}
