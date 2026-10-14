package com.lowcortisol.backend.wishlist;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WishlistProductRequest(@NotNull @Positive Long productId) {}
