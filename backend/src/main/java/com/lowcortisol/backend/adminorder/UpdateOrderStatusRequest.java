package com.lowcortisol.backend.adminorder;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(@NotBlank @Size(max = 20) String status,
    @NotNull @Min(0) Long expectedVersion, @NotBlank @Size(max = 500) String reason) {}
