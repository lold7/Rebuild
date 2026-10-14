package com.lowcortisol.backend.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.cart.CartItemResponse;
import com.lowcortisol.backend.cart.CartResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<OrderHeader> headers;
    private final RowMapper<OrderItemResponse> items;

    public OrderRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        headers = (rs, row) -> new OrderHeader(rs.getLong("order_id"), rs.getString("request_fingerprint"),
            rs.getString("status"), rs.getString("payment_status"), rs.getString("payment_method"), rs.getString("currency"),
            rs.getInt("total_items"), rs.getLong("total_quantity"), rs.getBigDecimal("total_price"),
            read(rs, "address_snapshot"), read(rs, "payment_snapshot"), rs.getObject("created_at", OffsetDateTime.class));
        items = (rs, row) -> new OrderItemResponse(rs.getLong("order_item_id"), read(rs, "product_snapshot"),
            rs.getInt("quantity"), read(rs, "selected_attributes"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total"));
    }

    Optional<OrderHeader> byKey(long userId, String key) {
        return jdbc.query("SELECT * FROM orders WHERE user_id = ? AND idempotency_key = ?", headers, userId, key).stream().findFirst();
    }

    Optional<OrderHeader> owned(long userId, long id) {
        return jdbc.query("SELECT * FROM orders WHERE user_id = ? AND order_id = ?", headers, userId, id).stream().findFirst();
    }

    long historyCount(long userId, String status) {
        String sql = "SELECT count(*) FROM orders WHERE user_id = ?";
        return status == null ? jdbc.queryForObject(sql, Long.class, userId)
            : jdbc.queryForObject(sql + " AND status = ?", Long.class, userId, status);
    }

    List<OrderResponse> history(long userId, int page, int pageSize, String status) {
        String sql = "SELECT * FROM orders WHERE user_id = ?";
        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);
        if (status != null) {
            sql += " AND status = ?";
            parameters.add(status);
        }
        sql += " ORDER BY created_at DESC, order_id DESC LIMIT ? OFFSET ?";
        parameters.add(pageSize);
        parameters.add((long) (page - 1) * pageSize);
        var pageHeaders = jdbc.query(sql, headers, parameters.toArray());
        if (pageHeaders.isEmpty()) return List.of();

        // Fetch all page items together rather than issuing one query per order.
        Map<Long, List<OrderItemResponse>> grouped = itemsForOrders(pageHeaders.stream().map(OrderHeader::id).toList());
        return pageHeaders.stream().map(header -> response(header, grouped.getOrDefault(header.id(), List.of()))).toList();
    }

    public Map<Long, List<OrderItemResponse>> itemsForOrders(List<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(orderIds.size(), "?"));
        var rows = jdbc.query("SELECT * FROM order_items WHERE order_id IN (" + placeholders + ") ORDER BY order_id, order_item_id",
            (rs, row) -> Map.entry(rs.getLong("order_id"), items.mapRow(rs, row)),
            orderIds.toArray());
        Map<Long, List<OrderItemResponse>> grouped = new HashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(row.getKey(), ignored -> new ArrayList<>()).add(row.getValue()));
        return grouped;
    }

    long insert(long userId, Long addressId, Long savedPaymentMethodId, String key, String fingerprint,
        String paymentMethod, JsonNode address, JsonNode payment, CartResponse cart) {
        return jdbc.queryForObject("""
            INSERT INTO orders (user_id, address_id, saved_payment_method_id, idempotency_key, request_fingerprint,
                payment_method, address_snapshot, payment_snapshot, total_items, total_quantity, total_price)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?) RETURNING order_id
            """, Long.class, userId, addressId, savedPaymentMethodId, key, fingerprint, paymentMethod,
            address.toString(), payment == null ? null : payment.toString(), cart.totalItems(), cart.totalQuantity(), cart.totalPrice());
    }

    void addItem(long orderId, CartItemResponse item) {
        var product = item.product();
        var snapshot = json.createObjectNode();
        snapshot.put("productId", product.productId());
        snapshot.put("productName", product.productName());
        snapshot.put("categoryId", product.categoryId());
        snapshot.put("categoryName", product.categoryName());
        snapshot.set("productImages", product.productImages());
        snapshot.set("productAttributes", product.productAttributes());
        jdbc.update("""
            INSERT INTO order_items (order_id, product_id, product_snapshot, selected_attributes, quantity, unit_price)
            VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)
            """, orderId, product.productId(), snapshot.toString(), item.selectedAttributes().toString(), item.quantity(), product.productPrice());
    }

    void deductStock(long productId, long quantity) {
        if (jdbc.update("""
            UPDATE products SET product_quantity = product_quantity - ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE product_id = ? AND product_quantity >= ?
            """, quantity, productId, quantity) != 1) {
            throw OrderException.conflict("INSUFFICIENT_STOCK", "There is not enough stock to place this order");
        }
    }

    OrderResponse response(OrderHeader header) {
        List<OrderItemResponse> orderItems = jdbc.query("SELECT * FROM order_items WHERE order_id = ? ORDER BY order_item_id", items, header.id());
        return response(header, orderItems);
    }

    private OrderResponse response(OrderHeader header, List<OrderItemResponse> orderItems) {
        return new OrderResponse(header.id(), header.status(), header.paymentStatus(), header.paymentMethod(), header.currency(),
            header.totalItems(), header.totalQuantity(), header.totalPrice(), header.address(), header.payment(), orderItems, header.createdAt());
    }

    private JsonNode read(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null) return null;
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid order snapshot in database", exception);
        }
    }

    record OrderHeader(long id, String fingerprint, String status, String paymentStatus, String paymentMethod,
        String currency, int totalItems, long totalQuantity, java.math.BigDecimal totalPrice,
        JsonNode address, JsonNode payment, OffsetDateTime createdAt) {}
}
