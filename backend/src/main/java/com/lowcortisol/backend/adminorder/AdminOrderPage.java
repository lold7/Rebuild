package com.lowcortisol.backend.adminorder;

import java.util.List;

public record AdminOrderPage(List<AdminOrderResponse> items, int page, int pageSize, long totalItems, long totalPages) {}
