package com.lowcortisol.backend.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowcortisol.backend.catalog.ProductResponse;
import java.math.BigDecimal;

public record CartItemResponse(long cartItemId, ProductResponse product, int quantity, JsonNode selectedAttributes,
    BigDecimal lineTotal, boolean available, boolean sufficientStock, boolean validFormat) {}
