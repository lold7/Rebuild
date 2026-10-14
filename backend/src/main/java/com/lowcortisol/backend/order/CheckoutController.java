package com.lowcortisol.backend.order;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutController {
    private final CheckoutService checkout;

    public CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @GetMapping("/api/checkout")
    public CheckoutResponse preview(Authentication authentication) {
        return checkout.preview(authentication.getName());
    }
}
