package com.lowcortisol.backend.wishlist;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/wishlist")
public class WishlistController {
    private final WishlistService wishlist;

    public WishlistController(WishlistService wishlist) {
        this.wishlist = wishlist;
    }

    @GetMapping
    public WishlistResponse list(Authentication authentication) {
        return wishlist.list(authentication.getName());
    }

    @GetMapping("/{productId}")
    public WishlistStatusResponse status(@PathVariable String productId, Authentication authentication) {
        long id;
        try {
            if (!productId.matches("[0-9]+")) throw new NumberFormatException();
            id = Long.parseLong(productId);
        } catch (NumberFormatException exception) {
            throw new InvalidWishlistRequestException();
        }
        return new WishlistStatusResponse(id, wishlist.contains(authentication.getName(), id));
    }

    @PostMapping("/add")
    public WishlistActionResponse add(@Valid @RequestBody WishlistProductRequest request, Authentication authentication) {
        return wishlist.add(authentication.getName(), request.productId());
    }

    @PostMapping("/remove")
    public WishlistActionResponse remove(@Valid @RequestBody WishlistProductRequest request, Authentication authentication) {
        return wishlist.remove(authentication.getName(), request.productId());
    }

    @PostMapping("/toggle")
    public WishlistActionResponse toggle(@Valid @RequestBody WishlistProductRequest request, Authentication authentication) {
        return wishlist.toggle(authentication.getName(), request.productId());
    }

    public record WishlistStatusResponse(long productId, boolean wishlisted) {}
}
