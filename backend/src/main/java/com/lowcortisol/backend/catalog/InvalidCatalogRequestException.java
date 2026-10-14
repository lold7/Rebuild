package com.lowcortisol.backend.catalog;

public class InvalidCatalogRequestException extends RuntimeException {
    public InvalidCatalogRequestException(String message) {
        super(message);
    }
}
