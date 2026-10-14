package com.lowcortisol.backend.payment;

import java.util.List;

public record PaymentMethodsResponse(List<PaymentMethodResponse> paymentMethods, int totalMethods,
    Long defaultPaymentMethodId) {}
