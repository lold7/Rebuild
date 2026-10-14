package com.lowcortisol.backend.admincategory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryWriteRequest(@NotBlank @Size(max = 100) String categoryName, @Size(max = 120) String slug,
    @Size(max = 500) String categoryImage, Boolean isVisible, @Min(0) Long expectedVersion) {}
