package com.lowcortisol.backend.order;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(long orderId, String status, String paymentStatus, String paymentMethod, String currency,
    int totalItems, long totalQuantity, BigDecimal totalPrice, JsonNode address, JsonNode savedPaymentMethod,
    List<OrderItemResponse> items, OffsetDateTime createdAt) {}
