package com.lowcortisol.backend.payment;

import java.time.OffsetDateTime;

public record PaymentMethodResponse(long paymentMethodId, String type, String brand, String cardholderName,
    String last4, int expiryMonth, int expiryYear, String currency, boolean isDefault, boolean expired,
    OffsetDateTime createdAt) {}
