package com.lowcortisol.backend.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RemoveCartItemRequest(@NotNull @Positive Long cartItemId) {}
