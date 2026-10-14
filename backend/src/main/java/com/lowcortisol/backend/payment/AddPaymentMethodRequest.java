package com.lowcortisol.backend.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddPaymentMethodRequest(
    @NotBlank @Size(max = 10) String type,
    @NotBlank @Size(max = 30) String brand,
    @NotBlank @Size(max = 200) String cardholderName,
    @NotBlank @Pattern(regexp = "[0-9]{4}") String last4,
    @NotNull @Min(1) @Max(12) Integer expiryMonth,
    @NotNull @Min(2000) @Max(9999) Integer expiryYear,
    @Pattern(regexp = "[a-zA-Z]{3}") String currency,
    boolean isDefault
) {}
