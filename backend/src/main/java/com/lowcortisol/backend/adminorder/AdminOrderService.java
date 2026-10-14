package com.lowcortisol.backend.adminorder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@PreAuthorize("hasRole('ADMIN') && #email == authentication.name")
public class AdminOrderService {
    private static final int PAGE_SIZE = 15;
    private static final Set<String> STATUSES = Set.of("pending", "processing", "completed", "cancelled");
    private final AdminOrderRepository orders;

    public AdminOrderService(AdminOrderRepository orders) { this.orders = orders; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminOrderPage list(String email, String page, String status, String query, String customerId) {
        int number = page(page);
        String filter = status == null ? null : status(status);
        String keyword = query == null ? "" : query.trim();
        if (keyword.length() > 255) throw AdminOrderException.invalid("q must contain at most 255 characters");
        return orders.page(number, PAGE_SIZE, filter, keyword, customerId == null ? null : id(customerId));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminOrderResponse get(String email, long id) { return orders.response(order(id, false)); }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderStatusChangePage history(String email, long id, String page) {
        int number = page(page);
        order(id, false);
        return orders.history(id, number, PAGE_SIZE);
    }

    @Transactional
    public AdminOrderResponse updateStatus(String email, long id, @NotNull @Valid UpdateOrderStatusRequest request) {
        String target = status(request.status());
        var current = order(id, true);
        if (request.expectedVersion() != current.version()) throw AdminOrderException.stale();
        if (current.status().equals(target)) return orders.response(current);
        long adminId = orders.adminId(email);
        if (current.stockReleased() && !target.equals("cancelled")) {
            // Reversing a cancellation takes the books out of stock again, or fails without changing anything.
            var products = orders.lockStock(id);
            var quantities = orders.quantities(id);
            for (var product : products) {
                if (quantities.getOrDefault(product.id(), 0L) > product.quantity()) throw AdminOrderException.insufficientStock();
            }
            for (var product : products) {
                long quantity = quantities.getOrDefault(product.id(), 0L);
                if (quantity > 0) orders.reinstate(product, quantity, id, adminId);
            }
        } else if (target.equals("cancelled")) {
            // Lock products in checkout's order, then read live item references after any competing product deletion.
            var products = orders.lockStock(id);
            var quantities = orders.quantities(id);
            for (var product : products) {
                long quantity = quantities.getOrDefault(product.id(), 0L);
                if (quantity > Integer.MAX_VALUE - product.quantity()) throw AdminOrderException.overflow();
            }
            for (var product : products) {
                long quantity = quantities.getOrDefault(product.id(), 0L);
                if (quantity > 0) orders.restore(product, quantity, id, adminId);
            }
        }
        orders.status(current, target, adminId, request.reason());
        return orders.response(order(id, false));
    }

    private AdminOrderRepository.Header order(long id, boolean lock) {
        if (id < 1) throw AdminOrderException.invalid("Invalid order ID");
        return orders.get(id, lock).orElseThrow(AdminOrderException::missing);
    }

    private String status(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw AdminOrderException.invalid("status must be pending, processing, completed, or cancelled");
        return normalized;
    }

    private int page(String value) {
        try {
            if (value == null || !value.matches("[0-9]+")) throw new NumberFormatException();
            int number = Integer.parseInt(value);
            if (number < 1) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) { throw AdminOrderException.invalid("page must be a positive 32-bit integer"); }
    }

    static long id(String value) {
        try {
            if (value == null || !value.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(value);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) { throw AdminOrderException.invalid("Invalid order or customer ID"); }
    }
}
