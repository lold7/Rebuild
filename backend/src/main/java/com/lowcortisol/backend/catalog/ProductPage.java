package com.lowcortisol.backend.catalog;

import java.util.List;

public record ProductPage(List<ProductResponse> items, int page, int pageSize, long totalItems, long totalPages) {}
