package com.lowcortisol.backend.order;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerOrderHistoryController {
    private final OrderService orders;

    public CustomerOrderHistoryController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/api/account/orders")
    public OrderPage history(@RequestParam(defaultValue = "1") String page,
        @RequestParam(required = false) String status, Authentication authentication) {
        return orders.history(authentication.getName(), page, status);
    }
}
