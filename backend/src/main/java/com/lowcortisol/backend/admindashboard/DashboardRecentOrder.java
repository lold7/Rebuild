package com.lowcortisol.backend.admindashboard;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DashboardRecentOrder(long orderId, long userId, String username, String status,
    String paymentStatus, String paymentMethod, int totalItems, long totalQuantity,
    BigDecimal totalPrice, String currency, OffsetDateTime createdAt) {}
