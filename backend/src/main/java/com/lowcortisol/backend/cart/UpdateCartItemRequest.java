package com.lowcortisol.backend.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateCartItemRequest(@NotNull @Positive Long cartItemId, @NotNull @Min(1) @Max(10) Integer quantity) {}
