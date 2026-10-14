package com.lowcortisol.backend.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<CartItemResponse> items, int totalItems, long totalQuantity, BigDecimal totalPrice, boolean valid) {}
