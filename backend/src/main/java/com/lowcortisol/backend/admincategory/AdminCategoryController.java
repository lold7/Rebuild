package com.lowcortisol.backend.admincategory;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {
    private final AdminCategoryService categories;

    public AdminCategoryController(AdminCategoryService categories) { this.categories = categories; }

    @GetMapping
    public List<AdminCategoryResponse> list(@RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) String visibility, Authentication auth) {
        return categories.list(auth.getName(), q, visibility);
    }

    @GetMapping("/{id}")
    public AdminCategoryResponse get(@PathVariable String id, Authentication auth) {
        return categories.get(auth.getName(), AdminCategoryService.id(id));
    }

    @PostMapping
    public ResponseEntity<AdminCategoryResponse> create(@Valid @RequestBody CategoryWriteRequest request, Authentication auth) {
        var category = categories.create(auth.getName(), request);
        return ResponseEntity.created(URI.create("/api/admin/categories/" + category.categoryId())).body(category);
    }

    @PutMapping("/{id}")
    public AdminCategoryResponse update(@PathVariable String id, @Valid @RequestBody CategoryWriteRequest request, Authentication auth) {
        return categories.update(auth.getName(), AdminCategoryService.id(id), request);
    }

    @PutMapping("/{id}/visibility")
    public AdminCategoryResponse visibility(@PathVariable String id, @Valid @RequestBody CategoryVisibilityRequest request, Authentication auth) {
        return categories.visibility(auth.getName(), AdminCategoryService.id(id), request);
    }

    @PutMapping("/{id}/toggle")
    public AdminCategoryResponse toggle(@PathVariable String id, @Valid @RequestBody CategoryVersionRequest request, Authentication auth) {
        return categories.toggle(auth.getName(), AdminCategoryService.id(id), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @RequestParam(required = false) String expectedVersion, Authentication auth) {
        categories.delete(auth.getName(), AdminCategoryService.id(id), AdminCategoryService.number(expectedVersion));
        return ResponseEntity.noContent().build();
    }
}
