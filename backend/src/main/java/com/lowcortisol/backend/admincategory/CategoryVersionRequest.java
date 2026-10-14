package com.lowcortisol.backend.admincategory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CategoryVersionRequest(@NotNull @Min(0) Long expectedVersion) {}
