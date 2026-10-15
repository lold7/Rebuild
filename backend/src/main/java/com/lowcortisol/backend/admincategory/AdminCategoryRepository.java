package com.lowcortisol.backend.admincategory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AdminCategoryRepository {
    private static final String SELECT = """
        SELECT c.*, (SELECT count(*) FROM products p WHERE p.category_id = c.category_id) AS product_count
        FROM categories c
        """;
    private final JdbcTemplate jdbc;
    private final RowMapper<AdminCategoryResponse> mapper = (rs, row) -> new AdminCategoryResponse(
        rs.getLong("category_id"), rs.getString("category_name"), rs.getString("slug"), rs.getString("category_image"),
        rs.getBoolean("is_visible"), rs.getLong("product_count"), rs.getLong("version"),
        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));

    public AdminCategoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<AdminCategoryResponse> list(String query, String visibility) {
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return jdbc.query(SELECT + """
            WHERE (? OR c.is_visible = ?)
              AND (? OR c.category_name ILIKE ? ESCAPE '!' OR c.slug ILIKE ? ESCAPE '!')
            ORDER BY c.category_id
            """, mapper, visibility.equals("all"), visibility.equals("visible"), query.isEmpty(), pattern, pattern);
    }

    Optional<AdminCategoryResponse> get(long id, boolean lock) {
        if (lock) {
            return jdbc.query(SELECT + " WHERE c.category_id = ? FOR UPDATE OF c", mapper, id).stream().findFirst();
        }
        return jdbc.query(SELECT + " WHERE c.category_id = ?", mapper, id).stream().findFirst();
    }

    long insert(CategoryWriteRequest request, String slug, String image) {
        return Objects.requireNonNull(jdbc.queryForObject("""
            INSERT INTO categories (category_name, slug, category_image, is_visible)
            VALUES (?, ?, ?, ?) RETURNING category_id
            """, Long.class, request.categoryName().trim(), slug, image, request.isVisible() == null || request.isVisible()),
            "Inserted category ID must not be null");
    }

    void update(long id, CategoryWriteRequest request, String slug, String image, boolean visible) {
        if (jdbc.update("""
            UPDATE categories SET category_name = ?, slug = ?, category_image = ?, is_visible = ?,
                version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE category_id = ? AND version = ?
            """, request.categoryName().trim(), slug, image, visible, id, request.expectedVersion()) != 1) throw AdminCategoryException.stale();
    }

    void visibility(long id, boolean visible, long version) {
        if (jdbc.update("""
            UPDATE categories SET is_visible = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE category_id = ? AND version = ?
            """, visible, id, version) != 1) throw AdminCategoryException.stale();
    }

    boolean hasProducts(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM products WHERE category_id = ?)", Boolean.class, id));
    }

    void delete(long id, long version) {
        if (jdbc.update("DELETE FROM categories WHERE category_id = ? AND version = ?", id, version) != 1) throw AdminCategoryException.stale();
    }
}
