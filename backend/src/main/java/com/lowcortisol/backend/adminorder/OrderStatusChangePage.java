package com.lowcortisol.backend.adminorder;

import java.util.List;

public record OrderStatusChangePage(List<OrderStatusChangeResponse> items, int page, int pageSize, long totalItems, long totalPages) {}
