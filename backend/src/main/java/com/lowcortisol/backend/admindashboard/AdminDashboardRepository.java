package com.lowcortisol.backend.admindashboard;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminDashboardRepository {
    private final JdbcTemplate jdbc;

    public AdminDashboardRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    OffsetDateTime generatedAt() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class),
            "Dashboard generation timestamp must not be null");
    }

    long recentOrderCount() {
        return Objects.requireNonNull(jdbc.queryForObject(
            "SELECT count(*) FROM orders o JOIN users u ON u.user_id = o.user_id AND u.role = 'customer'", Long.class),
            "Recent order count must not be null");
    }

    List<DashboardRecentOrder> recentOrders(int page, int size) {
        return jdbc.query("""
            SELECT o.order_id, o.user_id, u.username, o.status, o.payment_status, o.payment_method,
                o.total_items, o.total_quantity, o.total_price, o.currency, o.created_at
            FROM orders o JOIN users u ON u.user_id = o.user_id AND u.role = 'customer'
            ORDER BY o.created_at DESC, o.order_id DESC LIMIT ? OFFSET ?
            """, (rs, row) -> new DashboardRecentOrder(rs.getLong("order_id"), rs.getLong("user_id"), rs.getString("username"),
                rs.getString("status"), rs.getString("payment_status"), rs.getString("payment_method"),
                rs.getInt("total_items"), rs.getLong("total_quantity"), rs.getBigDecimal("total_price"),
                rs.getString("currency"), rs.getObject("created_at", OffsetDateTime.class)), size, (long) (page - 1) * size);
    }

    List<DashboardDailySales> dailySales(LocalDate start, LocalDate end) {
        return jdbc.query("""
            SELECT * FROM admin_dashboard_daily_sales WHERE sales_date BETWEEN ? AND ? ORDER BY sales_date
            """, (rs, row) -> new DashboardDailySales(rs.getObject("sales_date", LocalDate.class),
                rs.getLong("total_orders"), rs.getLong("pending_orders"), rs.getLong("completed_orders"),
                rs.getLong("cancelled_orders"), rs.getBigDecimal("order_value"), rs.getBigDecimal("total_revenue")),
            start, end);
    }
}
