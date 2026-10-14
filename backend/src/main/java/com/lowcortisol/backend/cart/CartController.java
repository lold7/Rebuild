package com.lowcortisol.backend.cart;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public CartResponse view(Authentication authentication) {
        return cart.view(authentication.getName());
    }

    @PostMapping("/add")
    public CartResponse add(@Valid @RequestBody AddCartItemRequest request, Authentication authentication) {
        return cart.add(authentication.getName(), request);
    }

    @PostMapping("/update")
    public CartResponse update(@Valid @RequestBody UpdateCartItemRequest request, Authentication authentication) {
        return cart.update(authentication.getName(), request);
    }

    @PostMapping("/remove")
    public CartResponse remove(@Valid @RequestBody RemoveCartItemRequest request, Authentication authentication) {
        return cart.remove(authentication.getName(), request);
    }

    @PostMapping("/clear")
    public CartResponse clear(Authentication authentication) {
        return cart.clear(authentication.getName());
    }
}
