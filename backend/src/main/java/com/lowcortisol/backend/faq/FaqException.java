package com.lowcortisol.backend.faq;

import org.springframework.http.HttpStatus;

public class FaqException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    private FaqException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    static FaqException invalid(String message) {
        return new FaqException(HttpStatus.BAD_REQUEST, "INVALID_FAQ_REQUEST", message);
    }

    static FaqException missing() {
        return new FaqException(HttpStatus.NOT_FOUND, "FAQ_NOT_FOUND", "FAQ not found");
    }
}
