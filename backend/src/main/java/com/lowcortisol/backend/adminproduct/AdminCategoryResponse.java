package com.lowcortisol.backend.adminproduct;

public record AdminCategoryResponse(long categoryId, String categoryName, String slug, String categoryImage,
    boolean isVisible, long productCount) {}
