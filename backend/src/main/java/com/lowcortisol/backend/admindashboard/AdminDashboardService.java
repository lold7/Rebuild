package com.lowcortisol.backend.admindashboard;

import com.lowcortisol.backend.admincustomer.AdminCustomerService;
import com.lowcortisol.backend.adminproduct.AdminProductService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN') && #email == authentication.name")
public class AdminDashboardService {
    private final AdminDashboardRepository dashboard;
    private final AdminCustomerService customers;
    private final AdminProductService products;

    public AdminDashboardService(AdminDashboardRepository dashboard, AdminCustomerService customers, AdminProductService products) {
        this.dashboard = dashboard;
        this.customers = customers;
        this.products = products;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminDashboardResponse get(String email, String days) {
        int count = days(days);
        // Anchor both the report snapshot and the UTC date window to this PostgreSQL transaction.
        var generatedAt = dashboard.generatedAt().withOffsetSameInstant(ZoneOffset.UTC);
        var customerSummary = customers.summary(email, "", null);
        var inventory = products.summary(email, null);
        var recent = dashboard.recentOrders();
        var end = generatedAt.toLocalDate();
        var start = end.minusDays(count - 1L);
        var byDate = new HashMap<LocalDate, DashboardDailySales>();
        dashboard.dailySales(start, end).forEach(day -> byDate.put(day.date(), day));
        var daily = new ArrayList<DashboardDailySales>();
        long totalOrders = 0;
        BigDecimal orderValue = BigDecimal.ZERO.setScale(2), revenue = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < count; i++) {
            var date = start.plusDays(i);
            var day = byDate.getOrDefault(date, new DashboardDailySales(date, 0, 0, 0, 0,
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)));
            daily.add(day);
            totalOrders += day.totalOrders();
            orderValue = orderValue.add(day.orderValue());
            revenue = revenue.add(day.totalRevenue());
        }
        var trend = new DashboardSalesTrend(count, start, end, "UTC", totalOrders, orderValue, revenue, List.copyOf(daily));
        return new AdminDashboardResponse(inventory.totalProducts(), customerSummary.totalCustomers(), customerSummary.totalOrders(),
            customerSummary.totalSpent(), customerSummary.orderValue(), "USD", customerSummary.pendingOrders(),
            customerSummary.completedOrders(), customerSummary.cancelledOrders(), customerSummary.customersWithOrders(),
            customerSummary.customersWithoutOrders(), inventory, recent, trend, generatedAt);
    }

    private int days(String value) {
        if (value == null) return 30;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            int days = Integer.parseInt(value);
            if (days < 1 || days > 366) throw new NumberFormatException();
            return days;
        } catch (NumberFormatException exception) { throw new AdminDashboardException("days must be an integer between 1 and 366"); }
    }
}
