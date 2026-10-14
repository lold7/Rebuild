package com.lowcortisol.backend.adminproduct;

import com.lowcortisol.backend.catalog.ProductResponse;
import java.time.OffsetDateTime;

public record AdminProductResponse(ProductResponse product, boolean isVisible, boolean categoryVisible,
    long version, OffsetDateTime updatedAt) {}
