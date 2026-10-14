package com.lowcortisol.backend.payment;

import java.time.OffsetDateTime;
import java.time.YearMonth;

record PaymentMethod(long id, String type, String brand, String cardholderName, String last4,
    int expiryMonth, int expiryYear, String currency, boolean isDefault, OffsetDateTime createdAt) {
    boolean active(YearMonth today) {
        return !YearMonth.of(expiryYear, expiryMonth).isBefore(today);
    }

    PaymentMethodResponse response(YearMonth today) {
        return new PaymentMethodResponse(id, type, brand, cardholderName, last4, expiryMonth, expiryYear, currency,
            isDefault, !active(today), createdAt);
    }
}
