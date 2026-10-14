package com.lowcortisol.backend.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(@Positive Long addressId, @NotBlank @Size(max = 30) String paymentMethod,
    @Positive Long savedPaymentMethodId) {}
