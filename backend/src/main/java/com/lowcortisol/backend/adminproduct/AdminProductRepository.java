package com.lowcortisol.backend.adminproduct;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowcortisol.backend.catalog.ProductRowMapper;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

@Repository
public class AdminProductRepository {
    private static final String SELECT = "SELECT p.*, c.category_name, c.slug AS category_slug, c.is_visible AS category_visible ";
    private static final String FROM = "FROM products p JOIN categories c ON c.category_id = p.category_id WHERE TRUE ";
    private static final String FILTERED_FROM = FROM + """
        AND (? OR p.category_id = ?)
        AND (? OR (p.is_visible AND c.is_visible))
        AND (? OR NOT (p.is_visible AND c.is_visible))
        AND (? OR p.product_quantity = 0)
        AND (? OR p.product_quantity BETWEEN 1 AND ?)
        AND (? OR p.product_name ILIKE ? ESCAPE '!'
            OR COALESCE(p.product_description, '') ILIKE ? ESCAPE '!'
            OR COALESCE(p.product_attributes ->> 'author', '') ILIKE ? ESCAPE '!'
            OR COALESCE(p.product_attributes ->> 'sku', '') ILIKE ? ESCAPE '!')
        """;
    private final JdbcTemplate jdbc;
    private final RowMapper<AdminProductResponse> mapper;
    private final RowMapper<InventoryAdjustmentResponse> adjustments = (rs, row) -> new InventoryAdjustmentResponse(
        rs.getLong("adjustment_id"), rs.getLong("product_id"), rs.getObject("admin_user_id", Long.class),
        rs.getInt("quantity_before"), rs.getInt("quantity_after"), rs.getLong("delta"), rs.getString("reason"),
        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("order_id", Long.class));

    public AdminProductRepository(JdbcTemplate jdbc, ProductRowMapper products) {
        this.jdbc = jdbc;
        mapper = (rs, row) -> new AdminProductResponse(products.mapRow(rs, row), rs.getBoolean("is_visible"),
            rs.getBoolean("category_visible"), rs.getLong("version"), rs.getObject("updated_at", OffsetDateTime.class));
    }

    Optional<AdminProductResponse> get(long id, boolean lock) {
        if (lock) {
            return jdbc.query(SELECT + FROM + " AND p.product_id = ? FOR UPDATE OF p", mapper, id).stream().findFirst();
        }
        return jdbc.query(SELECT + FROM + " AND p.product_id = ?", mapper, id).stream().findFirst();
    }

    void requireCategory(long id, boolean lock) {
        boolean exists;
        if (lock) {
            exists = !jdbc.query("SELECT category_id FROM categories WHERE category_id = ? FOR KEY SHARE",
                (rs, row) -> rs.getLong("category_id"), id).isEmpty();
        } else {
            exists = !jdbc.query("SELECT category_id FROM categories WHERE category_id = ?",
                (rs, row) -> rs.getLong("category_id"), id).isEmpty();
        }
        if (!exists) throw AdminProductException.missing("Category");
    }

    long adminId(String email) {
        return jdbc.query("SELECT user_id FROM users WHERE email = ? AND role = 'admin'", (rs, row) -> rs.getLong("user_id"), email)
            .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Admin does not exist"));
    }

    AdminProductPage page(int page, int size, String query, Long category, String visibility, String stock, int threshold) {
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Object[] args = {category == null, category, !visibility.equals("visible"), !visibility.equals("hidden"),
            !stock.equals("out"), !stock.equals("low"), threshold, query.isEmpty(), pattern, pattern, pattern, pattern};
        long count = Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) " + FILTERED_FROM, Long.class, args),
            "Product count must not be null");
        Object[] pageArgs = Arrays.copyOf(args, args.length + 2);
        pageArgs[args.length] = size;
        pageArgs[args.length + 1] = (long) (page - 1) * size;
        var items = jdbc.query(SELECT + FILTERED_FROM + " ORDER BY p.created_at DESC, p.product_id DESC LIMIT ? OFFSET ?", mapper, pageArgs);
        return new AdminProductPage(items, page, size, count, pages(count, size));
    }

    long insert(ProductWriteRequest request, JsonNode images, JsonNode attributes) {
        return Objects.requireNonNull(jdbc.queryForObject("""
            INSERT INTO products (category_id, product_name, product_description, product_price, product_images,
                product_attributes, product_quantity, is_visible)
            VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?) RETURNING product_id
            """, Long.class, request.categoryId(), request.productName().trim(), request.productDescription(), request.productPrice(),
            images.toString(), attributes.toString(), request.productQuantity() == null ? 0 : request.productQuantity(),
            // A book added without stock starts hidden.
            (request.isVisible() == null || request.isVisible()) && request.productQuantity() != null && request.productQuantity() > 0),
            "Inserted product ID must not be null");
    }

    void update(long id, ProductWriteRequest request, JsonNode images, JsonNode attributes, int quantity, boolean visible) {
        if (jdbc.update("""
            UPDATE products SET category_id = ?, product_name = ?, product_description = ?, product_price = ?,
                product_images = CAST(? AS jsonb), product_attributes = CAST(? AS jsonb), product_quantity = ?,
                is_visible = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE product_id = ? AND version = ?
            """, request.categoryId(), request.productName().trim(), request.productDescription(), request.productPrice(),
            images.toString(), attributes.toString(), quantity, visible, id, request.expectedVersion()) != 1) throw AdminProductException.stale();
    }

    void stock(long id, int quantity, long version) {
        // Visibility follows stock: zero hides the book, restocking from zero shows it again,
        // and otherwise the admin's choice (including a manual hide) is kept.
        if (jdbc.update("""
            UPDATE products SET is_visible = CASE WHEN ? <= 0 THEN FALSE WHEN product_quantity <= 0 THEN TRUE ELSE is_visible END,
                product_quantity = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE product_id = ? AND version = ?
            """, quantity, quantity, id, version) != 1) throw AdminProductException.stale();
    }

    boolean hasOrders(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM order_items WHERE product_id = ?)", Boolean.class, id));
    }

    void visibility(long id, boolean visible, long version) {
        if (jdbc.update("""
            UPDATE products SET is_visible = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE product_id = ? AND version = ?
            """, visible, id, version) != 1) throw AdminProductException.stale();
    }

    void delete(long id, long version) {
        if (jdbc.update("DELETE FROM products WHERE product_id = ? AND version = ?", id, version) != 1) throw AdminProductException.stale();
    }

    void adjustment(long id, long adminId, int before, int after, String reason) {
        jdbc.update("INSERT INTO inventory_adjustments (product_id, admin_user_id, quantity_before, quantity_after, reason) VALUES (?, ?, ?, ?, ?)",
            id, adminId, before, after, reason.trim());
    }

    InventoryAdjustmentPage adjustments(long id, int page, int size) {
        long count = Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM inventory_adjustments WHERE product_id = ?",
            Long.class, id), "Inventory adjustment count must not be null");
        var items = jdbc.query("""
            SELECT * FROM inventory_adjustments WHERE product_id = ?
            ORDER BY created_at DESC, adjustment_id DESC LIMIT ? OFFSET ?
            """, adjustments, id, size, (long) (page - 1) * size);
        return new InventoryAdjustmentPage(items, page, size, count, pages(count, size));
    }

    InventorySummary summary(int threshold) {
        return jdbc.queryForObject("""
            SELECT count(*) AS total, COALESCE(sum(p.product_quantity), 0) AS units,
                count(*) FILTER (WHERE p.product_quantity = 0) AS empty,
                count(*) FILTER (WHERE p.product_quantity BETWEEN 1 AND ?) AS low,
                count(*) FILTER (WHERE p.is_visible AND c.is_visible) AS visible,
                count(*) FILTER (WHERE NOT (p.is_visible AND c.is_visible)) AS hidden
            FROM products p JOIN categories c ON c.category_id = p.category_id
            """, (rs, row) -> new InventorySummary(rs.getLong("total"), rs.getLong("units"), rs.getLong("empty"),
            rs.getLong("low"), rs.getLong("visible"), rs.getLong("hidden"), threshold), threshold);
    }

    private long pages(long count, int size) { return count / size + (count % size == 0 ? 0 : 1); }
}
