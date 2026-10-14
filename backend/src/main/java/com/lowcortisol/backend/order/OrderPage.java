package com.lowcortisol.backend.order;

import java.util.List;

public record OrderPage(List<OrderResponse> items, int page, int pageSize, long totalItems, long totalPages) {}
