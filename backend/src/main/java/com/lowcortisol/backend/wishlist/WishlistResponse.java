package com.lowcortisol.backend.wishlist;

import java.util.List;

public record WishlistResponse(List<WishlistItem> items, long totalItems) {}
