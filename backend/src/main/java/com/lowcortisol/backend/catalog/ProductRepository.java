package com.lowcortisol.backend.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {
    private static final String FROM_VISIBLE = """
        FROM products p JOIN categories c ON c.category_id = p.category_id
        WHERE p.is_visible = TRUE AND c.is_visible = TRUE
        """;
    private static final String SELECT_PRODUCT = "SELECT p.*, c.category_name, c.slug AS category_slug ";
    private static final String NEWEST_FIRST = " ORDER BY p.created_at DESC, p.product_id DESC ";
    private static final String FILTERED_FROM = FROM_VISIBLE + """
        AND (? OR p.category_id = ?)
        AND (? OR p.product_name ILIKE ? ESCAPE '!'
            OR COALESCE(p.product_description, '') ILIKE ? ESCAPE '!'
            OR COALESCE(p.product_attributes ->> 'author', '') ILIKE ? ESCAPE '!')
        """;
    private final JdbcTemplate jdbc;
    private final RowMapper<ProductResponse> mapper;

    public ProductRepository(JdbcTemplate jdbc, ProductRowMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    Optional<ProductResponse> findVisible(long id) {
        return jdbc.query(SELECT_PRODUCT + FROM_VISIBLE + " AND p.product_id = ?", mapper, id).stream().findFirst();
    }

    List<ProductResponse> featured() {
        return jdbc.query(SELECT_PRODUCT + FROM_VISIBLE + NEWEST_FIRST + " LIMIT 8", mapper);
    }

    ProductPage page(int page, int pageSize, Long categoryId, String query) {
        // Treat wildcard characters literally, rather than allowing a query to match the entire catalog.
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total = Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) " + FILTERED_FROM, Long.class,
            categoryId == null, categoryId, query.isEmpty(), pattern, pattern, pattern), "Product count must not be null");
        List<ProductResponse> items = jdbc.query(SELECT_PRODUCT + FILTERED_FROM + NEWEST_FIRST + " LIMIT ? OFFSET ?",
            mapper, categoryId == null, categoryId, query.isEmpty(), pattern, pattern, pattern,
            pageSize, ((long) page - 1) * pageSize);
        return new ProductPage(items, page, pageSize, total, (total + pageSize - 1) / pageSize);
    }
}
