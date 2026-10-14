package com.lowcortisol.backend.adminproduct;

import org.springframework.http.HttpStatus;

public class AdminProductException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AdminProductException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    static AdminProductException invalid(String message) {
        return new AdminProductException(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_PRODUCT_REQUEST", message);
    }

    static AdminProductException missing(String resource) {
        return new AdminProductException(HttpStatus.NOT_FOUND, "ADMIN_" + resource.toUpperCase(java.util.Locale.ROOT) + "_NOT_FOUND", resource + " not found");
    }

    static AdminProductException stale() {
        return new AdminProductException(HttpStatus.CONFLICT, "STALE_PRODUCT_VERSION", "Product changed. Reload it before saving.");
    }

    static AdminProductException skuInUse() {
        return new AdminProductException(HttpStatus.CONFLICT, "SKU_IN_USE", "SKU is already assigned to another product");
    }
}
