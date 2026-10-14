package com.lowcortisol.backend.adminproduct;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockUpdateRequest(@NotNull @Min(0) Integer quantity, @NotNull @Min(0) Long expectedVersion,
    @NotBlank @Size(max = 500) String reason) {}
