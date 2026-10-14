package com.lowcortisol.backend.adminproduct;

import java.util.List;

public record AdminProductPage(List<AdminProductResponse> items, int page, int pageSize, long totalItems, long totalPages) {}
