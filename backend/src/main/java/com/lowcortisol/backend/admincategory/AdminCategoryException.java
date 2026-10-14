package com.lowcortisol.backend.admincategory;

import org.springframework.http.HttpStatus;

public class AdminCategoryException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    private AdminCategoryException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    static AdminCategoryException invalid(String message) {
        return new AdminCategoryException(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_CATEGORY_REQUEST", message);
    }

    static AdminCategoryException missing() {
        return new AdminCategoryException(HttpStatus.NOT_FOUND, "ADMIN_CATEGORY_NOT_FOUND", "Category not found");
    }

    static AdminCategoryException stale() {
        return new AdminCategoryException(HttpStatus.CONFLICT, "STALE_CATEGORY_VERSION", "Category changed. Reload it before saving.");
    }

    static AdminCategoryException slugInUse() {
        return new AdminCategoryException(HttpStatus.CONFLICT, "CATEGORY_SLUG_IN_USE", "Category slug is already in use");
    }

    static AdminCategoryException notEmpty() {
        return new AdminCategoryException(HttpStatus.CONFLICT, "CATEGORY_NOT_EMPTY", "Move or remove the category's products before deleting it");
    }
}
