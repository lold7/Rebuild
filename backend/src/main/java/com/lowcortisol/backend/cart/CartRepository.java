package com.lowcortisol.backend.cart;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.catalog.ProductResponse;
import com.lowcortisol.backend.catalog.ProductRowMapper;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CartRepository {
    private static final String SELECT_ROWS = """
        SELECT ci.cart_item_id, ci.quantity, ci.selected_attributes, p.*, c.category_name, c.slug AS category_slug,
            (p.is_visible AND c.is_visible) AS available
        FROM cart_items ci JOIN products p ON p.product_id = ci.product_id
        JOIN categories c ON c.category_id = p.category_id WHERE ci.user_id = ?
        """;
    private final JdbcTemplate jdbc;
    private final ProductRowMapper products;
    private final RowMapper<CartRow> rows;

    public CartRepository(JdbcTemplate jdbc, ProductRowMapper products, ObjectMapper json) {
        this.jdbc = jdbc;
        this.products = products;
        this.rows = (rs, row) -> {
            try {
                return new CartRow(rs.getLong("cart_item_id"), products.mapRow(rs, row), rs.getInt("quantity"),
                    json.readTree(rs.getString("selected_attributes")), rs.getBoolean("available"));
            } catch (JsonProcessingException exception) {
                throw new SQLException("Invalid cart attributes in database", exception);
            }
        };
    }

    List<CartRow> list(long userId) {
        return jdbc.query(SELECT_ROWS + " ORDER BY ci.created_at DESC, ci.cart_item_id DESC", rows, userId);
    }

    void lockProductsForCheckout(long userId) {
        // Every checkout locks products in the same order to avoid competing-customer deadlocks.
        jdbc.query("""
            SELECT p.product_id FROM products p JOIN categories c ON c.category_id = p.category_id
            WHERE p.product_id IN (SELECT product_id FROM cart_items WHERE user_id = ?)
            ORDER BY p.product_id FOR UPDATE OF p FOR SHARE OF c
            """, (rs, row) -> rs.getLong("product_id"), userId);
    }

    Optional<CartRow> ownedItem(long userId, long cartItemId) {
        return jdbc.query(SELECT_ROWS + " AND ci.cart_item_id = ?", rows, userId, cartItemId).stream().findFirst();
    }

    Optional<CartRow> matchingItem(long userId, long productId, String format) {
        return jdbc.query(SELECT_ROWS + " AND ci.product_id = ? AND lower(COALESCE(ci.selected_attributes ->> 'format', '')) = lower(?)",
            rows, userId, productId, format).stream().findFirst();
    }

    Optional<CartProduct> lockProduct(long productId) {
        return jdbc.query("""
            SELECT p.*, c.category_name, c.slug AS category_slug, (p.is_visible AND c.is_visible) AS available
            FROM products p JOIN categories c ON c.category_id = p.category_id WHERE p.product_id = ? FOR SHARE OF p, c
            """, (rs, row) -> new CartProduct(products.mapRow(rs, row), rs.getBoolean("available")), productId).stream().findFirst();
    }

    long productQuantity(long userId, long productId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM cart_items WHERE user_id = ? AND product_id = ?",
            Long.class, userId, productId);
    }

    void add(long userId, long productId, int quantity, JsonNode attributes) {
        jdbc.update("INSERT INTO cart_items (user_id, product_id, quantity, selected_attributes) VALUES (?, ?, ?, CAST(? AS jsonb))",
            userId, productId, quantity, attributes.toString());
    }

    void update(long userId, long cartItemId, int quantity) {
        jdbc.update("UPDATE cart_items SET quantity = ? WHERE cart_item_id = ? AND user_id = ?", quantity, cartItemId, userId);
    }

    boolean remove(long userId, long cartItemId) {
        return jdbc.update("DELETE FROM cart_items WHERE cart_item_id = ? AND user_id = ?", cartItemId, userId) == 1;
    }

    void clear(long userId) {
        jdbc.update("DELETE FROM cart_items WHERE user_id = ?", userId);
    }

    record CartRow(long cartItemId, ProductResponse product, int quantity, JsonNode selectedAttributes, boolean available) {}
    record CartProduct(ProductResponse product, boolean available) {}
}
