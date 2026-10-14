package com.lowcortisol.backend.admincategory;

import java.time.OffsetDateTime;

public record AdminCategoryResponse(long categoryId, String categoryName, String slug, String categoryImage,
    boolean isVisible, long productCount, long version, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
