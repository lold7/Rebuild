package com.lowcortisol.backend.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
    private final JdbcTemplate jdbc;
    private final RowMapper<ProductResponse> mapper;

    public ProductRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.mapper = (rs, row) -> {
            int quantity = rs.getInt("product_quantity");
            try {
                return new ProductResponse(rs.getLong("product_id"), rs.getLong("category_id"),
                    rs.getString("category_name"), rs.getString("category_slug"), rs.getString("product_name"),
                    rs.getString("product_description"), rs.getBigDecimal("product_price"),
                    json.readTree(rs.getString("product_images")), json.readTree(rs.getString("product_attributes")),
                    quantity, quantity > 0, Math.min(10, quantity), rs.getObject("created_at", java.time.OffsetDateTime.class));
            } catch (JsonProcessingException exception) {
                throw new SQLException("Invalid product JSON in database", exception);
            }
        };
    }

    Optional<ProductResponse> findVisible(long id) {
        return jdbc.query(SELECT_PRODUCT + FROM_VISIBLE + " AND p.product_id = ?", mapper, id).stream().findFirst();
    }

    List<ProductResponse> featured() {
        return jdbc.query(SELECT_PRODUCT + FROM_VISIBLE + NEWEST_FIRST + " LIMIT 8", mapper);
    }

    ProductPage page(int page, int pageSize, Long categoryId, String query) {
        StringBuilder filter = new StringBuilder(FROM_VISIBLE);
        List<Object> parameters = new ArrayList<>();
        if (categoryId != null) {
            filter.append(" AND p.category_id = ?");
            parameters.add(categoryId);
        }
        if (!query.isEmpty()) {
            // Treat wildcard characters literally, rather than allowing a query to match the entire catalog.
            String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            filter.append("""
                 AND (p.product_name ILIKE ? ESCAPE '!'
                   OR COALESCE(p.product_description, '') ILIKE ? ESCAPE '!'
                   OR COALESCE(p.product_attributes ->> 'author', '') ILIKE ? ESCAPE '!')
                """);
            parameters.addAll(List.of(pattern, pattern, pattern));
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) " + filter, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(pageSize);
        pageParameters.add(((long) page - 1) * pageSize);
        List<ProductResponse> items = jdbc.query(SELECT_PRODUCT + filter + NEWEST_FIRST + " LIMIT ? OFFSET ?",
            mapper, pageParameters.toArray());
        return new ProductPage(items, page, pageSize, total, (total + pageSize - 1) / pageSize);
    }
}
