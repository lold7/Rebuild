package com.lowcortisol.backend.adminorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.order.OrderItemResponse;
import com.lowcortisol.backend.order.OrderRepository;
import com.lowcortisol.backend.order.OrderResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

@Repository
public class AdminOrderRepository {
    private static final String SELECT = "SELECT o.*, u.username, u.email, u.phone ";
    private static final String FROM = "FROM orders o JOIN users u ON u.user_id = o.user_id WHERE TRUE ";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OrderRepository orderItems;
    private final RowMapper<Header> headers;
    private final RowMapper<OrderStatusChangeResponse> changes = (rs, row) -> new OrderStatusChangeResponse(
        rs.getLong("change_id"), rs.getLong("order_id"), rs.getObject("admin_user_id", Long.class),
        rs.getString("previous_status"), rs.getString("new_status"), rs.getLong("order_version"),
        rs.getString("reason"), rs.getObject("created_at", OffsetDateTime.class));

    public AdminOrderRepository(JdbcTemplate jdbc, ObjectMapper json, OrderRepository orderItems) {
        this.jdbc = jdbc;
        this.json = json;
        this.orderItems = orderItems;
        headers = (rs, row) -> new Header(rs.getLong("order_id"), rs.getString("status"), rs.getString("payment_status"),
            rs.getString("payment_method"), rs.getString("currency"), rs.getInt("total_items"), rs.getLong("total_quantity"),
            rs.getBigDecimal("total_price"), read(rs, "address_snapshot"), read(rs, "payment_snapshot"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getLong("version"), rs.getBoolean("stock_released"),
            rs.getObject("updated_at", OffsetDateTime.class), new OrderCustomerResponse(rs.getLong("user_id"),
                rs.getString("username"), rs.getString("email"), rs.getString("phone")));
    }

    Optional<Header> get(long id, boolean lock) {
        // A non-key lock serializes status changes while allowing foreign-key maintenance on order items.
        return jdbc.query(SELECT + FROM + " AND o.order_id = ?" + (lock ? " FOR NO KEY UPDATE OF o" : ""), headers, id).stream().findFirst();
    }

    AdminOrderPage page(int page, int size, String status, String query, Long customerId) {
        StringBuilder filter = new StringBuilder(FROM);
        List<Object> args = new ArrayList<>();
        if (status != null) { filter.append(" AND o.status = ?"); args.add(status); }
        if (customerId != null) { filter.append(" AND o.user_id = ?"); args.add(customerId); }
        if (!query.isEmpty()) {
            String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            filter.append(" AND (CAST(o.order_id AS text) ILIKE ? ESCAPE '!' OR u.username ILIKE ? ESCAPE '!' OR u.email ILIKE ? ESCAPE '!')");
            args.addAll(List.of(pattern, pattern, pattern));
        }
        long count = jdbc.queryForObject("SELECT count(*) " + filter, Long.class, args.toArray());
        args.add(size); args.add((long) (page - 1) * size);
        var pageHeaders = jdbc.query(SELECT + filter + " ORDER BY o.created_at DESC, o.order_id DESC LIMIT ? OFFSET ?", headers, args.toArray());
        Map<Long, List<OrderItemResponse>> items = orderItems.itemsForOrders(pageHeaders.stream().map(Header::id).toList());
        var result = pageHeaders.stream().map(header -> response(header, items.getOrDefault(header.id(), List.of()))).toList();
        return new AdminOrderPage(result, page, size, count, pages(count, size));
    }

    AdminOrderResponse response(Header header) {
        var items = orderItems.itemsForOrders(List.of(header.id()));
        return response(header, items.getOrDefault(header.id(), List.of()));
    }

    private AdminOrderResponse response(Header h, List<OrderItemResponse> items) {
        var order = new OrderResponse(h.id(), h.status(), h.paymentStatus(), h.paymentMethod(), h.currency(), h.totalItems(),
            h.totalQuantity(), h.totalPrice(), h.address(), h.payment(), items, h.createdAt());
        return new AdminOrderResponse(order, h.customer(), h.version(), h.stockReleased(), h.updatedAt());
    }

    long adminId(String email) {
        return jdbc.query("SELECT user_id FROM users WHERE email = ? AND role = 'admin'", (rs, row) -> rs.getLong("user_id"), email)
            .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Admin does not exist"));
    }

    List<Stock> lockStock(long orderId) {
        return jdbc.query("""
            SELECT p.product_id, p.product_quantity FROM products p
            WHERE p.product_id IN (SELECT product_id FROM order_items WHERE order_id = ?)
            ORDER BY p.product_id FOR UPDATE OF p
            """, (rs, row) -> new Stock(rs.getLong("product_id"), rs.getInt("product_quantity")), orderId);
    }

    Map<Long, Long> quantities(long orderId) {
        Map<Long, Long> result = new java.util.HashMap<>();
        jdbc.query("""
            SELECT product_id, sum(quantity) AS quantity FROM order_items
            WHERE order_id = ? AND product_id IS NOT NULL GROUP BY product_id
            """, (rs, row) -> Map.entry(rs.getLong("product_id"), rs.getLong("quantity")), orderId)
            .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    void restore(Stock product, long quantity, long orderId, long adminId) {
        if (jdbc.update("""
            UPDATE products SET product_quantity = product_quantity + ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE product_id = ? AND product_quantity <= ?
            """, quantity, product.id(), Integer.MAX_VALUE - quantity) != 1) throw AdminOrderException.overflow();
        jdbc.update("""
            INSERT INTO inventory_adjustments (product_id, admin_user_id, quantity_before, quantity_after, reason, order_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """, product.id(), adminId, product.quantity(), product.quantity() + quantity, "Cancelled order #" + orderId, orderId);
    }

    void status(Header order, String target, long adminId, String reason) {
        if (jdbc.update("""
            UPDATE orders SET status = ?, version = version + 1, stock_released = ?, updated_at = CURRENT_TIMESTAMP
            WHERE order_id = ? AND version = ?
            """, target, target.equals("cancelled"), order.id(), order.version()) != 1) throw AdminOrderException.stale();
        jdbc.update("""
            INSERT INTO order_status_changes (order_id, admin_user_id, previous_status, new_status, order_version, reason)
            VALUES (?, ?, ?, ?, ?, ?)
            """, order.id(), adminId, order.status(), target, order.version() + 1, reason.trim());
    }

    OrderStatusChangePage history(long id, int page, int size) {
        long count = jdbc.queryForObject("SELECT count(*) FROM order_status_changes WHERE order_id = ?", Long.class, id);
        var items = jdbc.query("""
            SELECT * FROM order_status_changes WHERE order_id = ? ORDER BY created_at DESC, change_id DESC LIMIT ? OFFSET ?
            """, changes, id, size, (long) (page - 1) * size);
        return new OrderStatusChangePage(items, page, size, count, pages(count, size));
    }

    private JsonNode read(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null) return null;
        try { return json.readTree(value); }
        catch (JsonProcessingException exception) { throw new SQLException("Invalid order snapshot in database", exception); }
    }

    private long pages(long count, int size) { return count / size + (count % size == 0 ? 0 : 1); }

    record Stock(long id, int quantity) {}
    record Header(long id, String status, String paymentStatus, String paymentMethod, String currency, int totalItems,
        long totalQuantity, BigDecimal totalPrice, JsonNode address, JsonNode payment, OffsetDateTime createdAt,
        long version, boolean stockReleased, OffsetDateTime updatedAt, OrderCustomerResponse customer) {}
}
