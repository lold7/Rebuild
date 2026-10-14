package com.lowcortisol.backend.admincategory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@PreAuthorize("hasRole('ADMIN') && #email == authentication.name")
public class AdminCategoryService {
    private final AdminCategoryRepository categories;

    public AdminCategoryService(AdminCategoryRepository categories) { this.categories = categories; }

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> list(String email, String query, String visibility) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.length() > 255) throw AdminCategoryException.invalid("q must contain at most 255 characters");
        String filter = visibility == null ? "all" : visibility.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("all", "visible", "hidden").contains(filter)) throw AdminCategoryException.invalid("Invalid visibility filter");
        return categories.list(keyword, filter);
    }

    @Transactional(readOnly = true)
    public AdminCategoryResponse get(String email, long id) { return category(id, false); }

    @Transactional
    public AdminCategoryResponse create(String email, @NotNull @Valid CategoryWriteRequest request) {
        if (request.expectedVersion() != null) throw AdminCategoryException.invalid("expectedVersion is only used for existing categories");
        String slug = request.slug() == null ? generatedSlug(request.categoryName().trim()) : slug(request.slug());
        String image = image(request.categoryImage());
        try {
            return category(categories.insert(request, slug, image), false);
        } catch (DuplicateKeyException exception) { throw AdminCategoryException.slugInUse(); }
    }

    @Transactional
    public AdminCategoryResponse update(String email, long id, @NotNull @Valid CategoryWriteRequest request) {
        AdminCategoryResponse current = category(id, true);
        version(current, request.expectedVersion());
        String slug = request.slug() == null ? current.slug() : slug(request.slug());
        String image = request.categoryImage() == null ? current.categoryImage() : image(request.categoryImage());
        boolean visible = request.isVisible() == null ? current.isVisible() : request.isVisible();
        try {
            categories.update(id, request, slug, image, visible);
        } catch (DuplicateKeyException exception) { throw AdminCategoryException.slugInUse(); }
        return category(id, false);
    }

    @Transactional
    public AdminCategoryResponse visibility(String email, long id, @NotNull @Valid CategoryVisibilityRequest request) {
        AdminCategoryResponse current = category(id, true);
        version(current, request.expectedVersion());
        if (current.isVisible() != request.isVisible()) categories.visibility(id, request.isVisible(), current.version());
        return category(id, false);
    }

    @Transactional
    public AdminCategoryResponse toggle(String email, long id, @NotNull @Valid CategoryVersionRequest request) {
        AdminCategoryResponse current = category(id, true);
        version(current, request.expectedVersion());
        categories.visibility(id, !current.isVisible(), current.version());
        return category(id, false);
    }

    @Transactional
    public void delete(String email, long id, Long expectedVersion) {
        AdminCategoryResponse current = category(id, true);
        version(current, expectedVersion);
        // Recheck references after acquiring the category lock, using a fresh READ COMMITTED snapshot.
        if (categories.hasProducts(id)) throw AdminCategoryException.notEmpty();
        try {
            categories.delete(id, current.version());
        } catch (DataIntegrityViolationException exception) { throw AdminCategoryException.notEmpty(); }
    }

    private AdminCategoryResponse category(long id, boolean lock) {
        if (id < 1) throw AdminCategoryException.invalid("Invalid category ID");
        return categories.get(id, lock).orElseThrow(AdminCategoryException::missing);
    }

    private void version(AdminCategoryResponse current, Long version) {
        if (version == null || version < 0) throw AdminCategoryException.invalid("expectedVersion is required and must be nonnegative");
        if (version != current.version()) throw AdminCategoryException.stale();
    }

    private String generatedSlug(String name) {
        String generated = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (generated.isEmpty()) generated = "category-" + UUID.randomUUID();
        if (!generated.matches(".*[a-z].*")) generated = "category-" + generated;
        return slug(generated);
    }

    private String slug(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 120 || !normalized.matches("[a-z0-9]+(-[a-z0-9]+)*") || !normalized.matches(".*[a-z].*")) {
            throw AdminCategoryException.invalid("slug must contain lowercase letters/digits separated by single hyphens, include a letter, and be at most 120 characters");
        }
        return normalized;
    }

    private String image(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        // Either an uploaded/frontend filename or an image hosted on another website (as in the original store).
        boolean filename = normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
        boolean webUrl = normalized.matches("https?://[^\\s\"'<>]+");
        if (!filename && !webUrl) throw AdminCategoryException.invalid("categoryImage must be a filename or an http(s) image URL");
        return normalized;
    }

    static long id(String value) {
        Long id = number(value);
        if (id == null || id < 1) throw AdminCategoryException.invalid("Invalid category ID");
        return id;
    }

    static Long number(String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException exception) { throw AdminCategoryException.invalid("Expected a nonnegative 64-bit integer"); }
    }
}
