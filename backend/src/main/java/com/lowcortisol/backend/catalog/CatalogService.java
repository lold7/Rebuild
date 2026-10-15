package com.lowcortisol.backend.catalog;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private static final String DIGITS = "\\d+";
    private static final int PAGE_SIZE = 15;
    private final ProductRepository products;
    private final CategoryRepository categories;

    public CatalogService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    public ProductResponse product(String identifier) {
        return products.findVisible(positiveId(identifier, "Product")).orElseThrow(() -> new CatalogNotFoundException("Product"));
    }

    public List<ProductResponse> featured() {
        return products.featured();
    }

    public List<CategoryResponse> categories() {
        return categories.visibleCategories();
    }

    public CategoryResponse category(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.length() > 120) throw new CatalogNotFoundException("Category");
        String value = identifier.trim().toLowerCase(Locale.ROOT);
        return (value.matches(DIGITS) ? categories.findVisibleById(positiveId(value, "Category")) : categories.findVisibleBySlug(value))
            .orElseThrow(() -> new CatalogNotFoundException("Category"));
    }

    // Keep the count, category visibility, and page contents on the same PostgreSQL snapshot.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductPage products(String page, String query, String category) {
        int pageNumber;
        try {
            if (page == null || !page.matches(DIGITS)) throw new NumberFormatException();
            pageNumber = Integer.parseInt(page);
            if (pageNumber < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new InvalidCatalogRequestException("page must be a positive integer no greater than 2147483647");
        }
        String keyword = query == null ? "" : query.trim();
        if (keyword.length() > 255) throw new InvalidCatalogRequestException("q must contain at most 255 characters");
        Long categoryId = category == null ? null : category(category).categoryId();
        return products.page(pageNumber, PAGE_SIZE, categoryId, keyword);
    }

    private long positiveId(String identifier, String resource) {
        try {
            if (identifier == null || !identifier.matches(DIGITS)) throw new NumberFormatException();
            long id = Long.parseLong(identifier);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new CatalogNotFoundException(resource);
        }
    }
}
