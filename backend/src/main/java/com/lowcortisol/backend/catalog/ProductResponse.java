package com.lowcortisol.backend.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductResponse(
    long productId, long categoryId, String categoryName, String categorySlug,
    String productName, String productDescription, BigDecimal productPrice,
    JsonNode productImages, JsonNode productAttributes, int productQuantity,
    boolean inStock, int maxQuantity, OffsetDateTime createdAt
) {}
