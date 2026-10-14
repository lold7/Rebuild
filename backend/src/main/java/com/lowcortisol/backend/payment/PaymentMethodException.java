package com.lowcortisol.backend.payment;

import org.springframework.http.HttpStatus;

public class PaymentMethodException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    private PaymentMethodException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    static PaymentMethodException invalid(String message) {
        return new PaymentMethodException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_METHOD_REQUEST", message);
    }

    static PaymentMethodException notFound() {
        return new PaymentMethodException(HttpStatus.NOT_FOUND, "PAYMENT_METHOD_NOT_FOUND", "Payment method not found");
    }

    static PaymentMethodException expired() {
        return new PaymentMethodException(HttpStatus.BAD_REQUEST, "PAYMENT_METHOD_EXPIRED", "Choose an unexpired payment method");
    }

    static PaymentMethodException lastActive() {
        return new PaymentMethodException(HttpStatus.CONFLICT, "LAST_ACTIVE_PAYMENT_METHOD",
            "Add another active payment method before removing this one");
    }
}
