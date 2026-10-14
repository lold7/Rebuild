package com.lowcortisol.backend.catalog;

public record CategoryResponse(long categoryId, String categoryName, String slug, String categoryImage) {}
