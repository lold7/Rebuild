package com.lowcortisol.backend.adminproduct;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProductVisibilityRequest(@NotNull Boolean isVisible, @NotNull @Min(0) Long expectedVersion) {}
