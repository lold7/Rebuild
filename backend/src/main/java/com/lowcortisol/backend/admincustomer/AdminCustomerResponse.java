package com.lowcortisol.backend.admincustomer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdminCustomerResponse(long userId, String username, String email, String phone,
    OffsetDateTime createdAt, OffsetDateTime updatedAt, long orderCount, long pendingOrderCount,
    long completedOrderCount, long cancelledOrderCount, BigDecimal orderValue, BigDecimal totalSpent,
    String currency, OffsetDateTime lastOrderAt) {}
