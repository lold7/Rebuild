package com.lowcortisol.backend.catalog;

public class CatalogNotFoundException extends RuntimeException {
    public CatalogNotFoundException(String resource) {
        super(resource + " does not exist or is no longer available.");
    }
}
