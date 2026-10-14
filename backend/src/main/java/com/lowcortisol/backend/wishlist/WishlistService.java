package com.lowcortisol.backend.wishlist;

import com.lowcortisol.backend.catalog.CatalogNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('CUSTOMER') && #email == authentication.name")
public class WishlistService {
    private final WishlistRepository wishlist;

    public WishlistService(WishlistRepository wishlist) {
        this.wishlist = wishlist;
    }

    @Transactional(readOnly = true)
    public WishlistResponse list(String email) {
        var items = wishlist.list(wishlist.customerId(email, false));
        return new WishlistResponse(items, items.size());
    }

    @Transactional(readOnly = true)
    public boolean contains(String email, long productId) {
        validate(productId);
        return wishlist.contains(wishlist.customerId(email, false), productId);
    }

    @Transactional
    public WishlistActionResponse add(String email, long productId) {
        validate(productId);
        long userId = wishlist.customerId(email, true);
        if (wishlist.contains(userId, productId)) return action("unchanged", productId, true);
        requireVisible(productId);
        return action(wishlist.add(userId, productId) ? "added" : "unchanged", productId, true);
    }

    @Transactional
    public WishlistActionResponse remove(String email, long productId) {
        validate(productId);
        return action(wishlist.remove(wishlist.customerId(email, true), productId) ? "removed" : "unchanged", productId, false);
    }

    @Transactional
    public WishlistActionResponse toggle(String email, long productId) {
        validate(productId);
        // Serialize each customer's writes, so concurrent toggles cannot both observe an absent entry.
        long userId = wishlist.customerId(email, true);
        if (wishlist.remove(userId, productId)) return action("removed", productId, false);
        requireVisible(productId);
        wishlist.add(userId, productId);
        return action("added", productId, true);
    }

    private void requireVisible(long productId) {
        if (!wishlist.lockVisibleProduct(productId)) throw new CatalogNotFoundException("Product");
    }

    private void validate(long productId) {
        if (productId < 1) throw new InvalidWishlistRequestException();
    }

    private WishlistActionResponse action(String action, long productId, boolean wishlisted) {
        return new WishlistActionResponse(true, action, productId, wishlisted);
    }
}
