package com.lowcortisol.backend.adminorder;

import org.springframework.http.HttpStatus;

public class AdminOrderException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    private AdminOrderException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    static AdminOrderException invalid(String message) {
        return new AdminOrderException(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_ORDER_REQUEST", message);
    }

    static AdminOrderException missing() {
        return new AdminOrderException(HttpStatus.NOT_FOUND, "ADMIN_ORDER_NOT_FOUND", "Order not found");
    }

    static AdminOrderException stale() {
        return new AdminOrderException(HttpStatus.CONFLICT, "STALE_ORDER_VERSION", "Order changed. Reload it before updating status.");
    }

    static AdminOrderException transition() {
        return new AdminOrderException(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS_TRANSITION", "Completed and cancelled orders cannot change status");
    }

    static AdminOrderException insufficientStock() {
        return new AdminOrderException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Not enough stock to reinstate this cancelled order");
    }

    static AdminOrderException overflow() {
        return new AdminOrderException(HttpStatus.CONFLICT, "STOCK_RESTORE_OVERFLOW", "Restored quantity would exceed the product's inventory limit");
    }
}
