package com.lowcortisol.backend.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<CategoryResponse> mapper = (rs, row) -> new CategoryResponse(
        rs.getLong("category_id"), rs.getString("category_name"), rs.getString("slug"), rs.getString("category_image"));

    public CategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<CategoryResponse> visibleCategories() {
        return jdbc.query("SELECT * FROM categories WHERE is_visible = TRUE ORDER BY category_id", mapper);
    }

    Optional<CategoryResponse> findVisibleById(long id) {
        return jdbc.query("SELECT * FROM categories WHERE category_id = ? AND is_visible = TRUE", mapper, id).stream().findFirst();
    }

    Optional<CategoryResponse> findVisibleBySlug(String slug) {
        return jdbc.query("SELECT * FROM categories WHERE slug = ? AND is_visible = TRUE", mapper, slug).stream().findFirst();
    }
}
