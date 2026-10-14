package com.lowcortisol.backend.auth;

import java.util.Locale;

enum UserRole {
    CUSTOMER, ADMIN;

    String databaseValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
