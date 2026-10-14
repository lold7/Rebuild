package com.lowcortisol.backend.adminproduct;

public record InventorySummary(long totalProducts, long totalUnits, long outOfStockProducts,
    long lowStockProducts, long visibleProducts, long hiddenProducts, int lowStockThreshold) {}
