package com.lowcortisol.backend.adminorder;

import com.lowcortisol.backend.order.OrderResponse;
import java.time.OffsetDateTime;

public record AdminOrderResponse(OrderResponse order, OrderCustomerResponse customer, long version,
    boolean stockReleased, OffsetDateTime updatedAt) {}
