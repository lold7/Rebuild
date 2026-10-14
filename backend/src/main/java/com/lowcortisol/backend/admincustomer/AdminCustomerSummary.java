package com.lowcortisol.backend.admincustomer;

import java.math.BigDecimal;

public record AdminCustomerSummary(long totalCustomers, long customersWithOrders, long customersWithoutOrders,
    long totalOrders, long pendingOrders, long completedOrders, long cancelledOrders,
    BigDecimal orderValue, BigDecimal totalSpent, String currency) {}
