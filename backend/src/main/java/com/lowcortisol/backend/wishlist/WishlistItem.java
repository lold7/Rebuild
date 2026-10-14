package com.lowcortisol.backend.wishlist;

import com.lowcortisol.backend.catalog.ProductResponse;
import java.time.OffsetDateTime;

public record WishlistItem(long wishlistId, ProductResponse product, boolean available, OffsetDateTime savedAt) {}
