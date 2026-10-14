package com.lowcortisol.backend.admindashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardSalesTrend(int days, LocalDate startDate, LocalDate endDate, String timeZone,
    long totalOrders, BigDecimal orderValue, BigDecimal totalRevenue, List<DashboardDailySales> items) {}
