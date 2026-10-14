package com.lowcortisol.backend.wishlist;

public class InvalidWishlistRequestException extends RuntimeException {
    public InvalidWishlistRequestException() {
        super("productId must be a positive 64-bit integer.");
    }
}
