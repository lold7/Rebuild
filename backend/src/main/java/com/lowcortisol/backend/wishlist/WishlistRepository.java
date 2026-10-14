package com.lowcortisol.backend.wishlist;

import com.lowcortisol.backend.catalog.ProductRowMapper;
import com.lowcortisol.backend.auth.CustomerAccountRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WishlistRepository {
    private final JdbcTemplate jdbc;
    private final ProductRowMapper products;
    private final CustomerAccountRepository customers;

    public WishlistRepository(JdbcTemplate jdbc, ProductRowMapper products, CustomerAccountRepository customers) {
        this.jdbc = jdbc;
        this.products = products;
        this.customers = customers;
    }

    long customerId(String email, boolean lock) {
        return customers.customerId(email, lock);
    }

    List<WishlistItem> list(long userId) {
        return jdbc.query("""
            SELECT w.wishlist_id, w.created_at AS saved_at, p.*, c.category_name, c.slug AS category_slug,
                (p.is_visible AND c.is_visible) AS available
            FROM wishlist w JOIN products p ON p.product_id = w.product_id
            JOIN categories c ON c.category_id = p.category_id
            WHERE w.user_id = ? ORDER BY w.created_at DESC, w.wishlist_id DESC
            """, (rs, row) -> new WishlistItem(rs.getLong("wishlist_id"), products.mapRow(rs, row),
                rs.getBoolean("available"), rs.getObject("saved_at", OffsetDateTime.class)), userId);
    }

    boolean contains(long userId, long productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM wishlist WHERE user_id = ? AND product_id = ?)", Boolean.class, userId, productId));
    }

    boolean lockVisibleProduct(long productId) {
        return !jdbc.queryForList("""
            SELECT p.product_id FROM products p JOIN categories c ON c.category_id = p.category_id
            WHERE p.product_id = ? AND p.is_visible = TRUE AND c.is_visible = TRUE FOR SHARE OF p, c
            """, Long.class, productId).isEmpty();
    }

    boolean add(long userId, long productId) {
        return jdbc.update("INSERT INTO wishlist (user_id, product_id) VALUES (?, ?) ON CONFLICT (user_id, product_id) DO NOTHING",
            userId, productId) == 1;
    }

    boolean remove(long userId, long productId) {
        return jdbc.update("DELETE FROM wishlist WHERE user_id = ? AND product_id = ?", userId, productId) == 1;
    }
}
