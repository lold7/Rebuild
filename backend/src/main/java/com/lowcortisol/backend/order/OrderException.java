package com.lowcortisol.backend.order;

import org.springframework.http.HttpStatus;

public class OrderException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public OrderException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    static OrderException invalid(String message) {
        return new OrderException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_REQUEST", message);
    }

    static OrderException conflict(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }

    static OrderException notFound() {
        return new OrderException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found");
    }
}
