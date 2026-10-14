package com.lowcortisol.backend.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddCartItemRequest(
    @NotNull @Positive Long productId,
    @Min(1) @Max(10) Integer quantity,
    @Size(max = 100) String selectedFormat
) {
    public AddCartItemRequest {
        if (quantity == null) quantity = 1;
    }
}
