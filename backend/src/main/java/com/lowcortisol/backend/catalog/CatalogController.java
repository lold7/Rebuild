package com.lowcortisol.backend.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping({"/api/products", "/api/products/search"})
    public ProductPage products(@RequestParam(defaultValue = "1") String page,
        @RequestParam(defaultValue = "") String q, @RequestParam(required = false) String category) {
        return catalog.products(page, q, category);
    }

    @GetMapping("/api/products/featured")
    public List<ProductResponse> featured() {
        return catalog.featured();
    }

    @GetMapping("/api/products/{id}")
    public ProductResponse product(@PathVariable String id) {
        return catalog.product(id);
    }

    @GetMapping("/api/categories")
    public List<CategoryResponse> categories() {
        return catalog.categories();
    }

    @GetMapping("/api/categories/{idOrSlug}")
    public CategoryResponse category(@PathVariable String idOrSlug) {
        return catalog.category(idOrSlug);
    }

    @GetMapping("/api/categories/{idOrSlug}/products")
    public ProductPage categoryProducts(@PathVariable String idOrSlug,
        @RequestParam(defaultValue = "1") String page, @RequestParam(defaultValue = "") String q) {
        return catalog.products(page, q, idOrSlug);
    }
}
