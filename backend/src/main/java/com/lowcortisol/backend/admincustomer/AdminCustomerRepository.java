package com.lowcortisol.backend.admincustomer;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AdminCustomerRepository {
    private static final String FROM = """
        FROM users u LEFT JOIN admin_customer_order_statistics s ON s.user_id = u.user_id
        WHERE u.role = 'customer'
        """;
    private static final String SELECT = """
        SELECT u.user_id, u.username, u.email, u.phone, u.created_at, u.updated_at,
            coalesce(s.order_count, 0) AS order_count,
            coalesce(s.pending_order_count, 0) AS pending_order_count,
            coalesce(s.completed_order_count, 0) AS completed_order_count,
            coalesce(s.cancelled_order_count, 0) AS cancelled_order_count,
            coalesce(s.order_value, 0::numeric) AS order_value,
            coalesce(s.total_spent, 0::numeric) AS total_spent, s.last_order_at
        """;
    private final JdbcTemplate jdbc;
    private final RowMapper<AdminCustomerResponse> customers = (rs, row) -> new AdminCustomerResponse(
        rs.getLong("user_id"), rs.getString("username"), rs.getString("email"), rs.getString("phone"),
        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
        rs.getLong("order_count"), rs.getLong("pending_order_count"), rs.getLong("completed_order_count"),
        rs.getLong("cancelled_order_count"), rs.getBigDecimal("order_value"), rs.getBigDecimal("total_spent"),
        "USD", rs.getObject("last_order_at", OffsetDateTime.class));

    public AdminCustomerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Optional<AdminCustomerResponse> get(long id) {
        return jdbc.query(SELECT + FROM + " AND u.user_id = ?", customers, id).stream().findFirst();
    }

    AdminCustomerPage page(int page, int size, String query, String activity, String sort) {
        var filter = filter(query, activity);
        long count = jdbc.queryForObject("SELECT count(*) " + filter.sql(), Long.class, filter.args().toArray());
        String order = switch (sort) {
            case "orders" -> "coalesce(s.order_count, 0) DESC, ";
            case "spent" -> "coalesce(s.total_spent, 0) DESC, ";
            default -> "";
        };
        List<Object> args = new ArrayList<>(filter.args());
        args.add(size); args.add((long) (page - 1) * size);
        var items = jdbc.query(SELECT + filter.sql() + " ORDER BY " + order + "u.created_at DESC, u.user_id DESC LIMIT ? OFFSET ?",
            customers, args.toArray());
        return new AdminCustomerPage(items, page, size, count, count / size + (count % size == 0 ? 0 : 1));
    }

    AdminCustomerSummary summary(String query, String activity) {
        var filter = filter(query, activity);
        return jdbc.queryForObject("""
            SELECT count(*) AS total_customers,
                count(*) FILTER (WHERE coalesce(s.order_count, 0) > 0) AS customers_with_orders,
                count(*) FILTER (WHERE coalesce(s.order_count, 0) = 0) AS customers_without_orders,
                coalesce(sum(s.order_count), 0) AS total_orders,
                coalesce(sum(s.pending_order_count), 0) AS pending_orders,
                coalesce(sum(s.completed_order_count), 0) AS completed_orders,
                coalesce(sum(s.cancelled_order_count), 0) AS cancelled_orders,
                coalesce(sum(s.order_value), 0::numeric) AS order_value,
                coalesce(sum(s.total_spent), 0::numeric) AS total_spent
            """ + filter.sql(), (rs, row) -> new AdminCustomerSummary(rs.getLong("total_customers"),
                rs.getLong("customers_with_orders"), rs.getLong("customers_without_orders"), rs.getLong("total_orders"),
                rs.getLong("pending_orders"), rs.getLong("completed_orders"), rs.getLong("cancelled_orders"),
                rs.getBigDecimal("order_value"), rs.getBigDecimal("total_spent"), "USD"), filter.args().toArray());
    }

    private Filter filter(String query, String activity) {
        StringBuilder sql = new StringBuilder(FROM);
        List<Object> args = new ArrayList<>();
        if (!query.isEmpty()) {
            String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            sql.append(" AND (u.username ILIKE ? ESCAPE '!' OR u.email ILIKE ? ESCAPE '!')");
            args.add(pattern); args.add(pattern);
        }
        if (activity.equals("ordered")) sql.append(" AND coalesce(s.order_count, 0) > 0");
        if (activity.equals("no-orders")) sql.append(" AND coalesce(s.order_count, 0) = 0");
        return new Filter(sql.toString(), args);
    }

    private record Filter(String sql, List<Object> args) {}
}
