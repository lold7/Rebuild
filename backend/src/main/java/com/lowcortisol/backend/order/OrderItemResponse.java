package com.lowcortisol.backend.order;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public record OrderItemResponse(long orderItemId, JsonNode product, int quantity, JsonNode selectedAttributes,
    BigDecimal unitPrice, BigDecimal lineTotal) {}
