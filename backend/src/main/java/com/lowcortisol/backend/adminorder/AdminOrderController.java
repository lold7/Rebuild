package com.lowcortisol.backend.adminorder;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final AdminOrderService orders;

    public AdminOrderController(AdminOrderService orders) { this.orders = orders; }

    @GetMapping
    public AdminOrderPage list(@RequestParam(defaultValue = "1") String page, @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "") String q, @RequestParam(required = false) String customerId, Authentication auth) {
        return orders.list(auth.getName(), page, status, q, customerId);
    }

    @GetMapping("/{id}")
    public AdminOrderResponse get(@PathVariable String id, Authentication auth) { return orders.get(auth.getName(), AdminOrderService.id(id)); }

    @PutMapping("/{id}/status")
    public AdminOrderResponse status(@PathVariable String id, @Valid @RequestBody UpdateOrderStatusRequest request, Authentication auth) {
        return orders.updateStatus(auth.getName(), AdminOrderService.id(id), request);
    }

    @GetMapping("/{id}/status-history")
    public OrderStatusChangePage history(@PathVariable String id, @RequestParam(defaultValue = "1") String page, Authentication auth) {
        return orders.history(auth.getName(), AdminOrderService.id(id), page);
    }
}
