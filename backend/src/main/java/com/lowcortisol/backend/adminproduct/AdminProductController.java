package com.lowcortisol.backend.adminproduct;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminProductController {
    private final AdminProductService products;

    public AdminProductController(AdminProductService products) { this.products = products; }

    @GetMapping({"/api/admin/products", "/api/admin/inventory"})
    public AdminProductPage list(@RequestParam(defaultValue = "1") String page, @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) String categoryId, @RequestParam(required = false) String visibility,
        @RequestParam(required = false) String stock, @RequestParam(required = false) String lowStockThreshold, Authentication auth) {
        return products.list(auth.getName(), page, q, categoryId, visibility, stock, lowStockThreshold);
    }

    @GetMapping("/api/admin/products/{id}")
    public AdminProductResponse get(@PathVariable String id, Authentication auth) { return products.get(auth.getName(), AdminProductService.id(id)); }

    @GetMapping("/api/admin/inventory/summary")
    public InventorySummary summary(@RequestParam(required = false) String lowStockThreshold, Authentication auth) {
        return products.summary(auth.getName(), lowStockThreshold);
    }

    @GetMapping("/api/admin/products/{id}/stock-history")
    public InventoryAdjustmentPage history(@PathVariable String id, @RequestParam(defaultValue = "1") String page, Authentication auth) {
        return products.adjustments(auth.getName(), AdminProductService.id(id), page);
    }

    @PostMapping("/api/admin/products")
    public ResponseEntity<AdminProductResponse> create(@Valid @RequestBody ProductWriteRequest request, Authentication auth) {
        var product = products.create(auth.getName(), request);
        return ResponseEntity.created(URI.create("/api/admin/products/" + product.product().productId())).body(product);
    }

    @PutMapping("/api/admin/products/{id}")
    public AdminProductResponse update(@PathVariable String id, @Valid @RequestBody ProductWriteRequest request, Authentication auth) {
        return products.update(auth.getName(), AdminProductService.id(id), request);
    }

    @PutMapping("/api/admin/products/{id}/stock")
    public AdminProductResponse stock(@PathVariable String id, @Valid @RequestBody StockUpdateRequest request, Authentication auth) {
        return products.stock(auth.getName(), AdminProductService.id(id), request);
    }

    @PutMapping("/api/admin/products/{id}/visibility")
    public AdminProductResponse visibility(@PathVariable String id, @Valid @RequestBody ProductVisibilityRequest request, Authentication auth) {
        return products.visibility(auth.getName(), AdminProductService.id(id), request);
    }

    @DeleteMapping("/api/admin/products/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @RequestParam(required = false) String expectedVersion, Authentication auth) {
        Long version = null;
        if (expectedVersion != null) {
            try {
                if (!expectedVersion.matches("\\d+")) throw new NumberFormatException();
                version = Long.parseLong(expectedVersion);
            } catch (NumberFormatException exception) { throw AdminProductException.invalid("Invalid expectedVersion"); }
        }
        products.delete(auth.getName(), AdminProductService.id(id), version);
        return ResponseEntity.noContent().build();
    }
}
