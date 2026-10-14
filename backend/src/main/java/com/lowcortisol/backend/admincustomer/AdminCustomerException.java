package com.lowcortisol.backend.admincustomer;

import org.springframework.http.HttpStatus;

public class AdminCustomerException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    private AdminCustomerException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    static AdminCustomerException invalid(String message) {
        return new AdminCustomerException(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_CUSTOMER_REQUEST", message);
    }

    static AdminCustomerException missing() {
        return new AdminCustomerException(HttpStatus.NOT_FOUND, "ADMIN_CUSTOMER_NOT_FOUND", "Customer not found");
    }
}
