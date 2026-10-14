package com.lowcortisol.backend.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.lowcortisol.backend.auth.CustomerAccountRepository;
import com.lowcortisol.backend.catalog.CatalogNotFoundException;
import com.lowcortisol.backend.catalog.ProductResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('CUSTOMER') && #email == authentication.name")
public class CartService {
    private final CartRepository cart;
    private final CustomerAccountRepository customers;

    public CartService(CartRepository cart, CustomerAccountRepository customers) {
        this.cart = cart;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public CartResponse view(String email) {
        return response(customers.customerId(email, false));
    }

    @Transactional
    public CartResponse add(String email, AddCartItemRequest request) {
        validateId(request.productId());
        validateQuantity(request.quantity());
        long userId = customers.customerId(email, true);
        ProductResponse product = visibleProduct(request.productId());
        if (!product.inStock()) throw stockError("OUT_OF_STOCK", "This book is out of stock.");
        String format = resolveFormat(product, request.selectedFormat());
        var existing = cart.matchingItem(userId, product.productId(), format);
        int newQuantity = existing.map(CartRepository.CartRow::quantity).orElse(0) + request.quantity();
        validateQuantity(newQuantity);
        requireStock(product, cart.productQuantity(userId, product.productId()) + request.quantity());
        if (existing.isPresent()) {
            cart.update(userId, existing.get().cartItemId(), newQuantity);
        } else {
            var attributes = JsonNodeFactory.instance.objectNode();
            if (!format.isEmpty()) attributes.put("format", format);
            cart.add(userId, product.productId(), request.quantity(), attributes);
        }
        return response(userId);
    }

    @Transactional
    public CartResponse update(String email, UpdateCartItemRequest request) {
        validateId(request.cartItemId());
        validateQuantity(request.quantity());
        long userId = customers.customerId(email, true);
        var item = cart.ownedItem(userId, request.cartItemId()).orElseThrow(this::missingItem);
        ProductResponse product = visibleProduct(item.product().productId());
        if (!validFormat(product, item.selectedAttributes())) throw formatError();
        requireStock(product, cart.productQuantity(userId, product.productId()) - item.quantity() + request.quantity());
        cart.update(userId, item.cartItemId(), request.quantity());
        return response(userId);
    }

    @Transactional
    public CartResponse remove(String email, RemoveCartItemRequest request) {
        validateId(request.cartItemId());
        long userId = customers.customerId(email, true);
        if (!cart.remove(userId, request.cartItemId())) throw missingItem();
        return response(userId);
    }

    @Transactional
    public CartResponse clear(String email) {
        long userId = customers.customerId(email, true);
        cart.clear(userId);
        return response(userId);
    }

    private CartResponse response(long userId) {
        var rows = cart.list(userId);
        Map<Long, Long> quantities = new HashMap<>();
        rows.forEach(row -> quantities.merge(row.product().productId(), (long) row.quantity(), Long::sum));
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        long totalQuantity = 0;
        for (var row : rows) {
            BigDecimal lineTotal = row.product().productPrice().multiply(BigDecimal.valueOf(row.quantity()));
            items.add(new CartItemResponse(row.cartItemId(), row.product(), row.quantity(), row.selectedAttributes(), lineTotal,
                row.available(), quantities.get(row.product().productId()) <= row.product().productQuantity(),
                validFormat(row.product(), row.selectedAttributes())));
            total = total.add(lineTotal);
            totalQuantity += row.quantity();
        }
        boolean valid = !items.isEmpty() && items.stream().allMatch(item -> item.available() && item.sufficientStock() && item.validFormat());
        return new CartResponse(items, items.size(), totalQuantity, total, valid);
    }

    private ProductResponse visibleProduct(long id) {
        var result = cart.lockProduct(id).orElseThrow(() -> new CatalogNotFoundException("Product"));
        if (!result.available()) throw new CatalogNotFoundException("Product");
        return result.product();
    }

    private List<String> formats(ProductResponse product) {
        JsonNode format = product.productAttributes().get("format");
        List<String> result = new ArrayList<>();
        if (format == null) return result;
        if (format.isTextual() && !format.asText().isBlank()) result.add(format.asText().trim());
        if (format.isArray()) format.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText().trim());
        });
        return result;
    }

    private String resolveFormat(ProductResponse product, String requested) {
        List<String> allowed = formats(product);
        String value = requested == null ? "" : requested.trim();
        if (allowed.isEmpty()) {
            if (!value.isEmpty()) throw formatError();
            return "";
        }
        if (value.isEmpty()) return allowed.get(0);
        return allowed.stream().filter(format -> format.equalsIgnoreCase(value)).findFirst().orElseThrow(this::formatError);
    }

    private boolean validFormat(ProductResponse product, JsonNode attributes) {
        List<String> allowed = formats(product);
        String selected = attributes.path("format").asText("");
        return selected.isEmpty() ? allowed.isEmpty() : allowed.stream().anyMatch(format -> format.equalsIgnoreCase(selected));
    }

    private void requireStock(ProductResponse product, long requested) {
        if (requested > product.productQuantity()) throw stockError(product.inStock() ? "INSUFFICIENT_STOCK" : "OUT_OF_STOCK",
            "There is not enough stock for this cart quantity across all formats.");
    }

    private void validateId(Long id) {
        if (id == null || id < 1) throw new CartException(HttpStatus.BAD_REQUEST, "INVALID_CART_REQUEST", "IDs must be positive 64-bit integers.");
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > 10) throw new CartException(HttpStatus.BAD_REQUEST, "QUANTITY_LIMIT",
            "Each cart line must contain between 1 and 10 units.");
    }

    private CartException formatError() {
        return new CartException(HttpStatus.BAD_REQUEST, "INVALID_FORMAT", "Choose an available book format.");
    }

    private CartException stockError(String code, String message) {
        return new CartException(HttpStatus.CONFLICT, code, message);
    }

    private CartException missingItem() {
        return new CartException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "This cart item does not exist.");
    }
}
