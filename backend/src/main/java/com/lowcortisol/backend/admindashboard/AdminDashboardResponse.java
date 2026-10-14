package com.lowcortisol.backend.admindashboard;

import com.lowcortisol.backend.adminproduct.InventorySummary;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminDashboardResponse(long totalProducts, long totalCustomers, long totalOrders,
    BigDecimal totalRevenue, BigDecimal orderValue, String currency, long pendingOrders,
    long completedOrders, long cancelledOrders, long customersWithOrders, long customersWithoutOrders,
    InventorySummary inventory, List<DashboardRecentOrder> recentOrders, DashboardSalesTrend salesTrend,
    OffsetDateTime generatedAt, int recentOrdersPage, long recentOrdersTotalPages) {}
