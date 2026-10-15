package com.lowcortisol.backend.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.address.AddressResponse;
import com.lowcortisol.backend.address.AddressService;
import com.lowcortisol.backend.auth.CustomerAccountRepository;
import com.lowcortisol.backend.cart.CartResponse;
import com.lowcortisol.backend.cart.CartService;
import com.lowcortisol.backend.payment.PaymentMethodService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN') && #email == authentication.name")
public class OrderService {
    private static final int HISTORY_PAGE_SIZE = 15;
    private static final Set<String> ORDER_STATUSES = Set.of("pending", "processing", "completed", "cancelled");
    private final CustomerAccountRepository customers;
    private final CartService cart;
    private final AddressService addresses;
    private final PaymentMethodService payments;
    private final OrderRepository orders;
    private final ObjectMapper json;

    public OrderService(CustomerAccountRepository customers, CartService cart, AddressService addresses,
        PaymentMethodService payments, OrderRepository orders, ObjectMapper json) {
        this.customers = customers;
        this.cart = cart;
        this.addresses = addresses;
        this.payments = payments;
        this.orders = orders;
        this.json = json;
    }

    @Transactional
    public OrderCreationResult create(String email, @NotNull @Valid CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")) {
            throw OrderException.invalid("Idempotency-Key must contain 1–100 letters, digits, dots, underscores, colons, or hyphens and start with a letter or digit");
        }
        String paymentMethod = paymentMethod(request.paymentMethod());
        String fingerprint = fingerprint(request, paymentMethod);
        long userId = customers.customerId(email, true);
        var previous = orders.byKey(userId, idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().fingerprint().equals(fingerprint)) {
                throw OrderException.conflict("IDEMPOTENCY_KEY_REUSED", "Use a new idempotency key for different order selections");
            }
            return new OrderCreationResult(orders.response(previous.get()), false);
        }

        AddressResponse address = address(email, request.addressId());
        JsonNode payment = payment(email, paymentMethod, request.savedPaymentMethodId());
        CartResponse currentCart = cart.lockForCheckout(email);
        validateCart(currentCart);
        Map<Long, Long> quantities = new TreeMap<>();
        currentCart.items().forEach(item -> quantities.merge(item.product().productId(), (long) item.quantity(), Long::sum));
        quantities.forEach(orders::deductStock);

        var identity = new OrderRepository.RequestIdentity(idempotencyKey, fingerprint);
        var paymentDetails = new OrderRepository.PaymentDetails(request.savedPaymentMethodId(), paymentMethod, payment);
        long orderId = orders.insert(userId, address.addressId(), identity, paymentDetails, addressSnapshot(address), currentCart);
        currentCart.items().forEach(item -> orders.addItem(orderId, item));
        cart.clear(email);
        return new OrderCreationResult(orders.response(orders.owned(userId, orderId).orElseThrow(OrderException::notFound)), true);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(String email, long id) {
        if (id < 1) throw OrderException.invalid("Invalid order ID");
        return orders.response(orders.owned(customers.customerId(email, false), id).orElseThrow(OrderException::notFound));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderPage history(String email, String page, String status) {
        int pageNumber;
        try {
            if (page == null || !page.matches("\\d+")) throw new NumberFormatException();
            pageNumber = Integer.parseInt(page);
            if (pageNumber < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw OrderException.invalid("page must be a positive integer no greater than 2147483647");
        }
        String filter = status == null ? null : status.trim().toLowerCase(Locale.ROOT);
        if (filter != null && !ORDER_STATUSES.contains(filter)) {
            throw OrderException.invalid("status must be pending, processing, completed, or cancelled");
        }
        long userId = customers.customerId(email, false);
        long total = orders.historyCount(userId, filter);
        var results = orders.history(userId, pageNumber, HISTORY_PAGE_SIZE, filter);
        long totalPages = total / HISTORY_PAGE_SIZE + (total % HISTORY_PAGE_SIZE == 0 ? 0 : 1);
        return new OrderPage(results, pageNumber, HISTORY_PAGE_SIZE, total, totalPages);
    }

    private AddressResponse address(String email, Long id) {
        if (id != null) return addresses.get(email, id);
        return addresses.list(email).addresses().stream().findFirst()
            .orElseThrow(() -> OrderException.invalid("Add a shipping address before placing an order"));
    }

    private JsonNode payment(String email, String method, Long id) {
        if (id == null) return null;
        var saved = payments.get(email, id);
        String expected = switch (method) {
            case "CREDIT_CARD" -> "CREDIT";
            case "DEBIT_CARD" -> "DEBIT";
            default -> throw OrderException.invalid("Saved cards can only be selected for card payment methods");
        };
        if (!expected.equals(saved.type())) throw OrderException.invalid("Saved card type does not match the payment method");
        if (saved.expired()) throw OrderException.invalid("Choose an unexpired saved card");
        if (!"USD".equals(saved.currency())) throw OrderException.invalid("Choose a USD saved card for this USD order");
        var snapshot = json.createObjectNode();
        snapshot.put("paymentMethodId", saved.paymentMethodId());
        snapshot.put("type", saved.type());
        snapshot.put("brand", saved.brand());
        snapshot.put("cardholderName", saved.cardholderName());
        snapshot.put("last4", saved.last4());
        snapshot.put("expiryMonth", saved.expiryMonth());
        snapshot.put("expiryYear", saved.expiryYear());
        snapshot.put("currency", saved.currency());
        return snapshot;
    }

    private JsonNode addressSnapshot(AddressResponse address) {
        var snapshot = json.createObjectNode();
        snapshot.put("addressId", address.addressId());
        snapshot.put("label", address.label());
        snapshot.put("fullName", address.fullName());
        snapshot.put("phone", address.phone());
        snapshot.put("addressLine", address.addressLine());
        snapshot.put("city", address.city());
        snapshot.put("province", address.province());
        snapshot.put("postalCode", address.postalCode());
        return snapshot;
    }

    private void validateCart(CartResponse current) {
        if (current.items().isEmpty()) throw OrderException.conflict("EMPTY_CART", "Add items before placing an order");
        if (current.items().stream().anyMatch(item -> !item.available())) {
            throw OrderException.conflict("PRODUCT_UNAVAILABLE", "Remove unavailable products before placing an order");
        }
        if (current.items().stream().anyMatch(item -> !item.validFormat())) {
            throw OrderException.conflict("INVALID_CART_FORMAT", "Choose an available format before placing an order");
        }
        if (current.items().stream().anyMatch(item -> !item.sufficientStock())) {
            throw OrderException.conflict("INSUFFICIENT_STOCK", "There is not enough stock to place this order");
        }
    }

    private String paymentMethod(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!CheckoutService.PAYMENT_METHODS.contains(normalized)) throw OrderException.invalid("Unsupported payment method");
        return normalized;
    }

    private String fingerprint(CreateOrderRequest request, String method) {
        String selections = request.addressId() + ":" + method + ":" + request.savedPaymentMethodId();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(selections.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
