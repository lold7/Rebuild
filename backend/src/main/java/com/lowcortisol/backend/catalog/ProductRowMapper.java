package com.lowcortisol.backend.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class ProductRowMapper implements RowMapper<ProductResponse> {
    private final ObjectMapper json;

    public ProductRowMapper(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public ProductResponse mapRow(ResultSet rs, int row) throws SQLException {
        int quantity = rs.getInt("product_quantity");
        try {
            return new ProductResponse(rs.getLong("product_id"), rs.getLong("category_id"),
                rs.getString("category_name"), rs.getString("category_slug"), rs.getString("product_name"),
                rs.getString("product_description"), rs.getBigDecimal("product_price"),
                json.readTree(rs.getString("product_images")), json.readTree(rs.getString("product_attributes")),
                quantity, quantity > 0, Math.min(10, quantity), rs.getObject("created_at", OffsetDateTime.class));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid product JSON in database", exception);
        }
    }
}
