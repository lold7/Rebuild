package com.lowcortisol.backend.adminproduct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@PreAuthorize("hasRole('ADMIN') && #email == authentication.name")
public class AdminProductService {
    private static final int PAGE_SIZE = 15;
    private final AdminProductRepository products;
    private final ObjectMapper json;

    public AdminProductService(AdminProductRepository products, ObjectMapper json) {
        this.products = products;
        this.json = json;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminProductPage list(String email, String page, String query, String category, String visibility, String stock, String threshold) {
        int number = page(page);
        String keyword = query == null ? "" : query.trim();
        if (keyword.length() > 255) throw AdminProductException.invalid("q must contain at most 255 characters");
        Long categoryId = category == null ? null : id(category);
        if (categoryId != null) products.requireCategory(categoryId, false);
        return products.page(number, PAGE_SIZE, keyword, categoryId,
            option(visibility, Set.of("all", "visible", "hidden")), option(stock, Set.of("all", "low", "out")), threshold(threshold));
    }

    @Transactional(readOnly = true)
    public AdminProductResponse get(String email, long id) { return product(id, false); }

    @Transactional(readOnly = true)
    public InventorySummary summary(String email, String threshold) { return products.summary(threshold(threshold)); }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InventoryAdjustmentPage adjustments(String email, long id, String page) {
        int number = page(page);
        product(id, false);
        return products.adjustments(id, number, PAGE_SIZE);
    }

    @Transactional
    public AdminProductResponse create(String email, @NotNull @Valid ProductWriteRequest request) {
        if (request.expectedVersion() != null) throw AdminProductException.invalid("expectedVersion is only used when editing an existing product");
        JsonNode attributes = attributes(request.productAttributes());
        JsonNode images = images(request.productImages());
        products.requireCategory(request.categoryId(), true);
        long adminId = products.adminId(email);
        long id;
        try {
            id = products.insert(request, images, attributes);
        } catch (DuplicateKeyException exception) {
            throw AdminProductException.skuInUse();
        }
        products.adjustment(id, adminId, 0, request.productQuantity() == null ? 0 : request.productQuantity(), "Initial stock");
        return product(id, false);
    }

    @Transactional
    public AdminProductResponse update(String email, long id, @NotNull @Valid ProductWriteRequest request) {
        AdminProductResponse current = product(id, true);
        version(current, request.expectedVersion());
        JsonNode attributes = request.productAttributes() == null ? current.product().productAttributes() : attributes(request.productAttributes());
        JsonNode images = request.productImages() == null ? current.product().productImages() : images(request.productImages());
        int quantity = request.productQuantity() == null ? current.product().productQuantity() : request.productQuantity();
        boolean visible = request.isVisible() == null ? current.isVisible() : request.isVisible();
        // Visibility follows stock: zero hides the book; restocking from zero shows it again
        // unless the admin chose otherwise in this request; a manual hide is otherwise kept.
        if (quantity <= 0) visible = false;
        else if (current.product().productQuantity() <= 0 && request.isVisible() == null) visible = true;
        products.requireCategory(request.categoryId(), true);
        long adminId = products.adminId(email);
        try {
            products.update(id, request, images, attributes, quantity, visible);
        } catch (DuplicateKeyException exception) {
            throw AdminProductException.skuInUse();
        }
        if (quantity != current.product().productQuantity()) {
            products.adjustment(id, adminId, current.product().productQuantity(), quantity, "Product edit");
        }
        return product(id, false);
    }

    @Transactional
    public AdminProductResponse stock(String email, long id, @NotNull @Valid StockUpdateRequest request) {
        AdminProductResponse current = product(id, true);
        version(current, request.expectedVersion());
        if (current.product().productQuantity() != request.quantity()) {
            long adminId = products.adminId(email);
            products.stock(id, request.quantity(), current.version());
            products.adjustment(id, adminId, current.product().productQuantity(), request.quantity(), request.reason());
        }
        return product(id, false);
    }

    @Transactional
    public AdminProductResponse visibility(String email, long id, @NotNull @Valid ProductVisibilityRequest request) {
        AdminProductResponse current = product(id, true);
        version(current, request.expectedVersion());
        if (current.isVisible() != request.isVisible()) products.visibility(id, request.isVisible(), current.version());
        return product(id, false);
    }

    @Transactional
    public void delete(String email, long id, Long expectedVersion) {
        AdminProductResponse current = product(id, true);
        version(current, expectedVersion);
        if (products.hasOrders(id)) throw AdminProductException.inOrders();
        products.delete(id, current.version());
    }

    private AdminProductResponse product(long id, boolean lock) {
        if (id < 1) throw AdminProductException.invalid("Invalid product ID");
        return products.get(id, lock).orElseThrow(() -> AdminProductException.missing("Product"));
    }

    private void version(AdminProductResponse current, Long version) {
        if (version == null || version < 0) throw AdminProductException.invalid("expectedVersion is required and must be nonnegative");
        if (current.version() != version) throw AdminProductException.stale();
    }

    private JsonNode images(List<String> values) { return values == null ? json.createArrayNode() : json.valueToTree(values); }

    private JsonNode attributes(JsonNode value) {
        if (value == null) return json.createObjectNode();
        if (!value.isObject() || value.size() > 50 || value.toString().getBytes(StandardCharsets.UTF_8).length > 16384) {
            throw AdminProductException.invalid("productAttributes must be an object with at most 50 fields and 16 KiB of JSON");
        }
        ObjectNode result = value.deepCopy();
        if (result.has("stock") || result.has("quantity") || result.has("productQuantity")) {
            throw AdminProductException.invalid("Inventory belongs in productQuantity, not productAttributes");
        }
        text(result, "author", 255);
        text(result, "isbn", 32);
        text(result, "sku", 100);
        if (result.has("sku") && result.get("sku").asText().isEmpty()) result.remove("sku");
        if (result.has("format")) {
            JsonNode format = result.get("format");
            if (format.isTextual()) {
                String normalized = format.asText().trim();
                if (normalized.isEmpty() || normalized.length() > 50) throw AdminProductException.invalid("Invalid format");
                result.put("format", normalized);
            } else if (format.isArray() && format.size() <= 10) {
                var formats = json.createArrayNode();
                Set<String> seen = new HashSet<>();
                for (JsonNode item : format) {
                    String normalized = item.isTextual() ? item.asText().trim() : "";
                    if (normalized.isEmpty() || normalized.length() > 50 || !seen.add(normalized.toLowerCase(Locale.ROOT))) {
                        throw AdminProductException.invalid("Formats must be distinct nonblank strings of at most 50 characters");
                    }
                    formats.add(normalized);
                }
                result.set("format", formats);
            } else throw AdminProductException.invalid("format must be a string or an array of up to 10 strings");
        }
        if (result.has("discount")) {
            JsonNode discount = result.get("discount");
            if (!discount.isNumber() || discount.decimalValue().compareTo(BigDecimal.ZERO) < 0
                || discount.decimalValue().compareTo(new BigDecimal("100")) > 0 || discount.decimalValue().stripTrailingZeros().scale() > 2) {
                throw AdminProductException.invalid("discount must be a percentage between 0 and 100 with at most two decimals");
            }
        }
        return result;
    }

    private void text(ObjectNode value, String key, int limit) {
        if (!value.has(key)) return;
        JsonNode field = value.get(key);
        if (!field.isTextual() || field.asText().length() > limit) throw AdminProductException.invalid("Invalid " + key);
        value.put(key, field.asText().trim());
    }

    private String option(String value, Set<String> choices) {
        String normalized = value == null ? "all" : value.trim().toLowerCase(Locale.ROOT);
        if (!choices.contains(normalized)) throw AdminProductException.invalid("Invalid product filter");
        return normalized;
    }

    private int threshold(String value) {
        int threshold = integer(value == null ? "5" : value);
        if (threshold > 100000) throw AdminProductException.invalid("lowStockThreshold must be between 0 and 100000");
        return threshold;
    }

    private int page(String value) {
        int number = integer(value);
        if (number < 1) throw AdminProductException.invalid("page must be a positive integer");
        return number;
    }

    private int integer(String value) {
        try {
            if (value == null || !value.matches("[0-9]+")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) { throw AdminProductException.invalid("Expected a nonnegative 32-bit integer"); }
    }

    static long id(String value) {
        try {
            if (value == null || !value.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(value);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) { throw AdminProductException.invalid("Invalid product or category ID"); }
    }
}
