package com.lowcortisol.backend.wishlist;

public record WishlistActionResponse(boolean success, String action, long productId, boolean wishlisted) {}
