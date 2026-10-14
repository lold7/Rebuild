package com.lowcortisol.backend.order;

public record OrderCreationResult(OrderResponse order, boolean created) {}
