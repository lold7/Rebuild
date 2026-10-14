package com.lowcortisol.backend.admindashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardDailySales(LocalDate date, long totalOrders, long pendingOrders,
    long completedOrders, long cancelledOrders, BigDecimal orderValue, BigDecimal totalRevenue) {}
