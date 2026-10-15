package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.catalog.CatalogDemoData;
import com.lowcortisol.backend.order.CheckoutService;
import com.lowcortisol.backend.order.CreateOrderRequest;
import com.lowcortisol.backend.order.OrderService;
import jakarta.validation.ConstraintViolationException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckoutIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "checkout-test-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> true);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired CatalogDemoData demo;
    @Autowired PasswordEncoder encoder;
    @Autowired CheckoutService checkout;
    @Autowired OrderService orders;

    @BeforeEach
    void prepare() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        demo.run(null);
    }

    @Test
    void checkoutPreviewsCartAddressesAndPaymentChoicesWithoutChangingStock() throws Exception {
        Browser browser = customer("alice@example.com");
        long card = browser.card("CREDIT", "USD");
        browser.add(1, 2, "Paperback");
        int before = stock(1);
        JsonNode preview = ok(browser.get("/api/checkout"));
        assertThat(preview.get("cart").get("totalPrice").decimalValue()).isEqualByComparingTo("36.00");
        assertThat(preview.get("cart").get("totalQuantity").asInt()).isEqualTo(2);
        assertThat(preview.get("addressBook").get("defaultAddressId").asLong()).isEqualTo(browser.addressId);
        assertThat(preview.get("savedPaymentMethods").get("defaultPaymentMethodId").asLong()).isEqualTo(card);
        assertThat(preview.get("paymentMethods").toString()).contains("CREDIT_CARD", "DEBIT_CARD", "BANK_TRANSFER", "DIGITAL_WALLET");
        assertThat(preview.get("ready").asBoolean()).isTrue();
        assertThat(preview.get("currency").asText()).isEqualTo("USD");
        assertThat(stock(1)).isEqualTo(before);
        assertThat(orderCount()).isZero();
        assertThat(ok(browser.get("/api/cart")).get("totalQuantity").asInt()).isEqualTo(2);
    }

    @Test
    void emptyMissingAddressAndStaleCartsAreReportedInPreview() throws Exception {
        Browser browser = customer("alice@example.com");
        assertThat(ok(browser.get("/api/checkout")).get("ready").asBoolean()).isFalse();
        browser.add(1, 2, "Paperback");
        jdbc.update("DELETE FROM addresses");
        assertThat(ok(browser.get("/api/checkout")).get("ready").asBoolean()).isFalse();
        browser.address("Replacement", true);
        jdbc.update("UPDATE products SET product_quantity = 1 WHERE product_id = 1");
        JsonNode preview = ok(browser.get("/api/checkout"));
        assertThat(preview.get("ready").asBoolean()).isFalse();
        assertThat(preview.get("cart").get("items").get(0).get("sufficientStock").asBoolean()).isFalse();
    }

    @Test
    void creationUsesCurrentPricesAndReturnsSafeOrderDetails() throws Exception {
        Browser alice = customer("alice@example.com");
        alice.add(1, 2, "Paperback");
        alice.add(1, 1, "Hardcover");
        alice.add(14, 2, "Paperback");
        jdbc.update("UPDATE products SET product_price = 0.10 WHERE product_id = 1");
        jdbc.update("UPDATE products SET product_price = 0.20 WHERE product_id = 14");
        HttpResponse<String> response = alice.place(Map.of("paymentMethod", "Bank Transfer"), "first-order");
        JsonNode order = created(response);
        assertThat(order.get("status").asText()).isEqualTo("pending");
        assertThat(order.get("paymentStatus").asText()).isEqualTo("unpaid");
        assertThat(order.get("paymentMethod").asText()).isEqualTo("BANK_TRANSFER");
        assertThat(order.get("totalPrice").decimalValue()).isEqualByComparingTo("0.70");
        assertThat(order.get("totalItems").asInt()).isEqualTo(3);
        assertThat(order.get("totalQuantity").asInt()).isEqualTo(5);
        assertThat(order.get("savedPaymentMethod").isNull()).isTrue();
        assertThat(order.get("address").get("addressLine").asText()).isEqualTo("Original shipping");
        assertThat(order.has("userId")).isFalse();
        assertThat(order.has("requestFingerprint")).isFalse();
        assertThat(order.has("idempotencyKey")).isFalse();
        assertThat(order.get("items").size()).isEqualTo(3);
        assertThat(ok(alice.get(response.headers().firstValue("Location").orElseThrow()))).isEqualTo(order);
    }

    @Test
    void creationAggregatesFormatsWhenDeductingStock() throws Exception {
        Browser alice = customer("alice@example.com");
        alice.add(1, 2, "Paperback");
        alice.add(1, 1, "Hardcover");
        alice.add(14, 2, "Paperback");
        int firstStock = stock(1);
        int secondStock = stock(14);
        created(alice.place(Map.of("paymentMethod", "Bank Transfer"), "stock-order"));
        assertThat(stock(1)).isEqualTo(firstStock - 3);
        assertThat(stock(14)).isEqualTo(secondStock - 2);
    }

    @Test
    void creationClearsOnlyTheOrderingCustomersCart() throws Exception {
        Browser alice = customer("alice@example.com");
        Browser bob = customer("bob@example.com");
        alice.add(1, 2, "Paperback");
        bob.add(1, 1, "Paperback");
        created(alice.place(Map.of("paymentMethod", "Bank Transfer"), "cart-order"));
        assertThat(ok(alice.get("/api/cart")).get("totalItems").asInt()).isZero();
        assertThat(ok(bob.get("/api/cart")).get("totalQuantity").asInt()).isEqualTo(1);
    }

    @Test
    void explicitAddressAndSavedCardAreSnapshottedAndDebitIsSupported() throws Exception {
        Browser browser = customer("alice@example.com");
        long address = browser.address("Chosen shipping", false);
        long card = browser.card("DEBIT", "USD");
        browser.add(1, 1, "Paperback");
        JsonNode order = created(browser.place(Map.of("addressId", address, "paymentMethod", "DEBIT_CARD", "savedPaymentMethodId", card), "debit-order"));
        assertThat(order.get("address").get("addressId").asLong()).isEqualTo(address);
        assertThat(order.get("address").get("addressLine").asText()).isEqualTo("Chosen shipping");
        assertThat(order.get("savedPaymentMethod").get("paymentMethodId").asLong()).isEqualTo(card);
        assertThat(order.get("savedPaymentMethod").get("last4").asText()).isEqualTo("0001");
        assertThat(order.get("savedPaymentMethod").get("type").asText()).isEqualTo("DEBIT");
        assertThat(order.get("savedPaymentMethod").has("cvv")).isFalse();
        assertThat(order.get("savedPaymentMethod").has("cardNumber")).isFalse();
        // Card orders are paid at checkout and complete immediately.
        assertThat(order.get("status").asText()).isEqualTo("completed");
        assertThat(order.get("paymentStatus").asText()).isEqualTo("paid");
    }

    @Test
    void cashOnDeliveryOrdersStayPendingAndUnpaid() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.address("Home", true);
        browser.add(1, 1, "Paperback");
        JsonNode order = created(browser.place(Map.of("paymentMethod", "CASH_ON_DELIVERY"), "cod-order"));
        assertThat(order.get("paymentMethod").asText()).isEqualTo("CASH_ON_DELIVERY");
        assertThat(order.get("status").asText()).isEqualTo("pending");
        assertThat(order.get("paymentStatus").asText()).isEqualTo("unpaid");
        assertThat(order.get("savedPaymentMethod").isNull()).isTrue();
    }

    @Test
    void retriesReturnOriginalOrderWithoutConsumingANewCartOrRecheckingDeletedSelections() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.add(1, 2, "Paperback");
        var request = Map.of("paymentMethod", "DIGITAL_WALLET", "addressId", browser.addressId);
        JsonNode original = created(browser.place(request, "retry-key"));
        int remaining = stock(1);
        browser.add(1, 1, "Paperback");
        jdbc.update("DELETE FROM addresses");
        jdbc.update("UPDATE products SET product_price = 99 WHERE product_id = 1");
        HttpResponse<String> replay = browser.place(request, "retry-key");
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(json.readTree(replay.body())).isEqualTo(original);
        assertThat(stock(1)).isEqualTo(remaining);
        assertThat(orderCount()).isEqualTo(1);
        assertThat(ok(browser.get("/api/cart")).get("totalQuantity").asInt()).isEqualTo(1);
    }

    @Test
    void changedSelectionsCannotReuseAKeyButDifferentCustomersCan() throws Exception {
        Browser alice = customer("alice@example.com");
        Browser bob = customer("bob@example.com");
        alice.add(1, 1, "Paperback");
        bob.add(1, 1, "Paperback");
        created(alice.place(Map.of("paymentMethod", "BANK_TRANSFER"), "same-key"));
        HttpResponse<String> conflict = alice.place(Map.of("paymentMethod", "DIGITAL_WALLET"), "same-key");
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(json.readTree(conflict.body()).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        created(bob.place(Map.of("paymentMethod", "BANK_TRANSFER"), "same-key"));
        assertThat(orderCount()).isEqualTo(2);
    }

    @Test
    void emptyCartAndMissingShippingAddressCannotCreateOrders() throws Exception {
        Browser browser = customer("alice@example.com");
        HttpResponse<String> empty = browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), "empty");
        assertThat(empty.statusCode()).isEqualTo(409);
        assertThat(json.readTree(empty.body()).get("code").asText()).isEqualTo("EMPTY_CART");
        browser.add(1, 1, "Paperback");
        int before = stock(1);
        jdbc.update("DELETE FROM addresses");
        assertThat(browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), "no-address").statusCode()).isEqualTo(400);
        assertNoOrderAndIntactCart(browser, before, 1);
    }

    @Test
    void hiddenProductsCategoriesAndInvalidFormatsCauseAtomicRejection() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.add(1, 1, "Paperback");
        browser.add(14, 1, "Paperback");
        int before = stock(1);
        jdbc.update("UPDATE products SET is_visible = FALSE WHERE product_id = 14");
        assertError(browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), "hidden-book"), 409, "PRODUCT_UNAVAILABLE");
        jdbc.update("UPDATE products SET is_visible = TRUE WHERE product_id = 14");
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 2");
        assertError(browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), "hidden-category"), 409, "PRODUCT_UNAVAILABLE");
        jdbc.update("UPDATE categories SET is_visible = TRUE WHERE category_id = 2");
        jdbc.update("UPDATE products SET product_attributes = '{\"format\":[\"Paperback\"]}'::jsonb WHERE product_id = 14");
        jdbc.update("UPDATE cart_items SET selected_attributes = '{\"format\":\"Hardcover\"}'::jsonb WHERE product_id = 14");
        assertError(browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), "stale-format"), 409, "INVALID_CART_FORMAT");
        assertNoOrderAndIntactCart(browser, before, 2);
    }

    @Test
    void stockIsCheckedAcrossFormatsAndEveryLineRollsBackOnInsufficientStock() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.add(1, 2, "Paperback");
        browser.add(1, 2, "Hardcover");
        browser.add(14, 1, "Paperback");
        jdbc.update("UPDATE products SET product_quantity = 3 WHERE product_id = 1");
        int secondStock = stock(14);
        assertError(browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), "insufficient"), 409, "INSUFFICIENT_STOCK");
        assertNoOrderAndIntactCart(browser, 3, 5);
        assertThat(stock(14)).isEqualTo(secondStock);
        jdbc.update("UPDATE products SET product_quantity = 0 WHERE product_id = 1");
        assertError(browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), "out-of-stock"), 409, "INSUFFICIENT_STOCK");
        assertThat(orderCount()).isZero();
    }

    @Test
    void foreignAndMissingAddressesAndCardsCannotBeUsed() throws Exception {
        Browser alice = customer("alice@example.com");
        Browser bob = customer("bob@example.com");
        long bobCard = bob.card("CREDIT", "USD");
        alice.add(1, 1, "Paperback");
        int before = stock(1);
        HttpResponse<String> foreignAddress = alice.place(Map.of("addressId", bob.addressId, "paymentMethod", "BANK_TRANSFER"), "foreign-address");
        HttpResponse<String> missingAddress = alice.place(Map.of("addressId", 999999, "paymentMethod", "BANK_TRANSFER"), "missing-address");
        assertThat(foreignAddress.statusCode()).isEqualTo(404);
        assertThat(foreignAddress.body()).isEqualTo(missingAddress.body());
        HttpResponse<String> foreignCard = alice.place(Map.of("paymentMethod", "CREDIT_CARD", "savedPaymentMethodId", bobCard), "foreign-card");
        HttpResponse<String> missingCard = alice.place(Map.of("paymentMethod", "CREDIT_CARD", "savedPaymentMethodId", 999999), "missing-card");
        assertThat(foreignCard.statusCode()).isEqualTo(404);
        assertThat(foreignCard.body()).isEqualTo(missingCard.body());
        assertNoOrderAndIntactCart(alice, before, 1);
    }

    @Test
    void expiredMismatchedAndNonUsdSavedCardsCannotBeSelected() throws Exception {
        Browser browser = customer("alice@example.com");
        long debit = browser.card("DEBIT", "USD");
        long euro = browser.card("CREDIT", "EUR");
        long expired = browser.card("CREDIT", "USD");
        YearMonth past = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        jdbc.update("UPDATE payment_methods SET expiry_month = ?, expiry_year = ? WHERE payment_method_id = ?",
            past.getMonthValue(), past.getYear(), expired);
        browser.add(1, 1, "Paperback");
        int before = stock(1);
        for (var request : List.of(Map.of("paymentMethod", "CREDIT_CARD", "savedPaymentMethodId", debit),
            Map.of("paymentMethod", "BANK_TRANSFER", "savedPaymentMethodId", debit),
            Map.of("paymentMethod", "CREDIT_CARD", "savedPaymentMethodId", euro),
            Map.of("paymentMethod", "CREDIT_CARD", "savedPaymentMethodId", expired))) {
            assertThat(browser.place(request, UUID.randomUUID().toString()).statusCode()).isEqualTo(400);
        }
        assertNoOrderAndIntactCart(browser, before, 1);
    }

    @Test
    void invalidRequestsClientTotalsAndIdentityInjectionAreRejected() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.add(1, 1, "Paperback");
        int before = stock(1);
        assertThat(browser.place(Map.of(), "invalid").statusCode()).isEqualTo(400);
        for (var field : List.of(Map.entry("paymentMethod", "Cash"), Map.entry("paymentMethod", " "),
            Map.entry("addressId", 0), Map.entry("addressId", 1.5), Map.entry("savedPaymentMethodId", -1),
            Map.entry("savedPaymentMethodId", 1.5), Map.entry("totalPrice", 0.01), Map.entry("items", List.of()),
            Map.entry("userId", 999), Map.entry("status", "completed"), Map.entry("paymentStatus", "paid"),
            Map.entry("currency", "EUR"), Map.entry("cardNumber", "4242424242424242"), Map.entry("cvv", "123"))) {
            Map<String, Object> request = new HashMap<>(Map.of("paymentMethod", "BANK_TRANSFER"));
            request.put(field.getKey(), field.getValue());
            assertThat(browser.place(request, UUID.randomUUID().toString()).statusCode()).as("invalid field %s", field.getKey()).isEqualTo(400);
        }
        for (String key : List.of("", "bad key", "x".repeat(101), "_invalid")) {
            assertThat(browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), key).statusCode()).isEqualTo(400);
        }
        assertThat(browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), null).statusCode()).isEqualTo(400);
        assertNoOrderAndIntactCart(browser, before, 1);
    }

    @Test
    void orderDetailsAreOwnedAndMalformedIdsAreRejected() throws Exception {
        Browser alice = customer("alice@example.com");
        Browser bob = customer("bob@example.com");
        alice.add(1, 1, "Paperback");
        long id = created(alice.place(Map.of("paymentMethod", "BANK_TRANSFER"), "owned-order")).get("orderId").asLong();
        HttpResponse<String> foreign = bob.get("/api/orders/" + id);
        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(foreign.body()).isEqualTo(bob.get("/api/orders/999999").body());
        for (String invalid : List.of("0", "-1", "abc", "1.5", "9223372036854775808")) {
            assertThat(alice.get("/api/orders/" + invalid).statusCode()).isEqualTo(400);
        }
        assertThat(orderCount()).isEqualTo(1);
    }

    @Test
    void guestsAdminsAndMissingCsrfCannotPreviewOrPlaceOrders() throws Exception {
        Browser guest = new Browser();
        assertThat(guest.get("/api/checkout").statusCode()).isEqualTo(401);
        assertThat(guest.get("/api/orders/1").statusCode()).isEqualTo(401);
        assertThat(guest.place(Map.of("paymentMethod", "BANK_TRANSFER"), "guest").statusCode()).isEqualTo(401);
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, 'admin')",
            "Admin", "admin@example.com", encoder.encode("correct-password"));
        Browser admin = new Browser();
        ok(admin.post("/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "correct-password")));
        // Admins can use the storefront checkout with their own (here empty) cart, but never see other users' orders.
        assertThat(admin.get("/api/checkout").statusCode()).isEqualTo(200);
        assertThat(admin.get("/api/orders/1").statusCode()).isEqualTo(404);
        assertThat(admin.place(Map.of("paymentMethod", "BANK_TRANSFER"), "admin").statusCode()).isNotIn(200, 201, 401, 403);
        Browser browser = customer("alice@example.com");
        browser.add(1, 1, "Paperback");
        int before = stock(1);
        assertThat(browser.send("/api/orders", Map.of("paymentMethod", "BANK_TRANSFER"), null, "no-csrf").statusCode()).isEqualTo(403);
        assertNoOrderAndIntactCart(browser, before, 1);
    }

    @Test
    void corsAllowsTheIdempotencyHeaderForTheConfiguredFrontend() throws Exception {
        var http = HttpClient.newHttpClient();
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/orders"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Content-Type, X-CSRF-TOKEN, Idempotency-Key").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow()).containsIgnoringCase("Idempotency-Key");
    }

    @Test
    void snapshotsSurviveCatalogEditsAndDeletionOfOriginalAddressCardAndProduct() throws Exception {
        Browser browser = customer("alice@example.com");
        long card = browser.card("CREDIT", "USD");
        browser.card("CREDIT", "USD");
        browser.add(1, 2, "Paperback");
        JsonNode original = created(browser.place(Map.of("paymentMethod", "CREDIT_CARD", "savedPaymentMethodId", card), "snapshots"));
        long id = original.get("orderId").asLong();
        jdbc.update("UPDATE products SET product_name = 'Changed name', product_price = 99, product_images = '[]'::jsonb WHERE product_id = 1");
        assertThat(ok(browser.get("/api/orders/" + id))).isEqualTo(original);
        ok(browser.post("/api/account/addresses/delete/" + browser.addressId, Map.of()));
        ok(browser.post("/api/account/payment-methods/delete/" + card, Map.of()));
        jdbc.update("DELETE FROM products WHERE product_id = 1");
        assertThat(ok(browser.get("/api/orders/" + id))).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT address_id FROM orders WHERE order_id = ?", Long.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT saved_payment_method_id FROM orders WHERE order_id = ?", Long.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT product_id FROM order_items WHERE order_id = ?", Long.class, id)).isNull();
    }

    @Test
    void ordersPersistAcrossAccountChangesAndCascadeOnAccountDeletion() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.add(1, 1, "Paperback");
        JsonNode original = created(browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), "account-stable"));
        long id = original.get("orderId").asLong();
        assertThat(browser.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
        ok(browser.post("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        assertThat(ok(browser.get("/api/orders/" + id))).isEqualTo(original);
        ok(browser.post("/api/account/profile", Map.of("username", "Alice", "email", "changed@example.com")));
        ok(browser.post("/api/auth/login", Map.of("email", "changed@example.com", "password", "correct-password")));
        ok(browser.post("/api/account/password", Map.of("currentPassword", "correct-password", "newPassword", "changed-password", "confirmPassword", "changed-password")));
        ok(browser.post("/api/auth/login", Map.of("email", "changed@example.com", "password", "changed-password")));
        assertThat(ok(browser.get("/api/orders/" + id))).isEqualTo(original);
        jdbc.update("DELETE FROM users WHERE email = 'changed@example.com'");
        assertThat(orderCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items", Integer.class)).isZero();
    }

    @Test
    void databaseFailureRollsBackHeaderItemsStockAndCartAndTheSameKeyCanBeRetried() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.add(1, 2, "Paperback");
        browser.add(14, 1, "Paperback");
        int before = stock(1);
        int secondBefore = stock(14);
        jdbc.execute("""
            CREATE FUNCTION reject_test_order_item() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN RAISE EXCEPTION 'Simulated order storage failure'; END $$
            """);
        jdbc.execute("CREATE TRIGGER reject_test_order_item BEFORE INSERT ON order_items FOR EACH ROW EXECUTE FUNCTION reject_test_order_item()");
        try {
            HttpResponse<String> failure = browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), "retry-after-failure");
            assertError(failure, 500, "DATABASE_ERROR");
            assertThat(failure.body()).doesNotContain("Simulated", "INSERT INTO", "Original shipping");
            assertNoOrderAndIntactCart(browser, before, 3);
            assertThat(stock(14)).isEqualTo(secondBefore);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items", Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_order_item ON order_items");
            jdbc.execute("DROP FUNCTION reject_test_order_item()");
        }
        created(browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), "retry-after-failure"));
        assertThat(stock(1)).isEqualTo(before - 2);
        assertThat(stock(14)).isEqualTo(secondBefore - 1);
    }

    @Test
    void serviceAuthorizationAndValidationCannotBeBypassed() throws Exception {
        customer("alice@example.com");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        try {
            assertThat(checkout.preview("alice@example.com").ready()).isFalse();
            assertThatThrownBy(() -> checkout.preview("other@example.com")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> orders.get("other@example.com", 1)).isInstanceOf(AccessDeniedException.class);
            var validRequest = new CreateOrderRequest(null, "BANK_TRANSFER", null);
            assertThatThrownBy(() -> orders.create("other@example.com", validRequest, "key"))
                .isInstanceOf(AccessDeniedException.class);
            var invalidAddressRequest = new CreateOrderRequest(-1L, "BANK_TRANSFER", null);
            assertThatThrownBy(() -> orders.create("alice@example.com", invalidAddressRequest, "key"))
                .isInstanceOf(ConstraintViolationException.class);
            // Admins may shop on the storefront, so only roles other than CUSTOMER/ADMIN are rejected.
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_GUEST"))));
            assertThatThrownBy(() -> checkout.preview("alice@example.com")).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void concurrentCustomersCannotOversellTheLastCopy() throws Exception {
        Browser alice = customer("alice@example.com");
        Browser bob = customer("bob@example.com");
        jdbc.update("UPDATE products SET product_quantity = 1 WHERE product_id = 1");
        alice.add(1, 1, "Paperback");
        bob.add(1, 1, "Paperback");
        var responses = concurrent(alice, Map.of("paymentMethod", "BANK_TRANSFER"), "alice-order",
            bob, Map.of("paymentMethod", "BANK_TRANSFER"), "bob-order");
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(201, 409);
        assertThat(stock(1)).isZero();
        assertThat(orderCount()).isEqualTo(1);
        int retainedQuantity = ok(alice.get("/api/cart")).get("totalQuantity").asInt() + ok(bob.get("/api/cart")).get("totalQuantity").asInt();
        assertThat(retainedQuantity).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyRequestsProduceOneOrderAndOneStockDeduction() throws Exception {
        Browser first = customer("alice@example.com");
        Browser second = login("alice@example.com");
        first.add(1, 2, "Paperback");
        int before = stock(1);
        var responses = concurrent(first, Map.of("paymentMethod", "BANK_TRANSFER"), "same-attempt",
            second, Map.of("paymentMethod", "BANK_TRANSFER"), "same-attempt");
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(201, 200);
        assertThat(json.readTree(responses.get(0).body())).isEqualTo(json.readTree(responses.get(1).body()));
        assertThat(orderCount()).isEqualTo(1);
        assertThat(stock(1)).isEqualTo(before - 2);
    }

    @Test
    void concurrentDifferentKeysCannotOrderTheSameCartTwice() throws Exception {
        Browser first = customer("alice@example.com");
        Browser second = login("alice@example.com");
        first.add(1, 2, "Paperback");
        int before = stock(1);
        var responses = concurrent(first, Map.of("paymentMethod", "BANK_TRANSFER"), "first-attempt",
            second, Map.of("paymentMethod", "BANK_TRANSFER"), "second-attempt");
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(201, 409);
        assertThat(orderCount()).isEqualTo(1);
        assertThat(stock(1)).isEqualTo(before - 2);
    }

    @Test
    void competingMultiProductOrdersLockInTheSameOrderWithoutDeadlocks() throws Exception {
        Browser alice = customer("alice@example.com");
        Browser bob = customer("bob@example.com");
        jdbc.update("UPDATE products SET product_quantity = 2 WHERE product_id IN (1, 14)");
        alice.add(1, 1, "Paperback");
        alice.add(14, 1, "Paperback");
        bob.add(14, 1, "Paperback");
        bob.add(1, 1, "Paperback");
        var responses = concurrent(alice, Map.of("paymentMethod", "BANK_TRANSFER"), "alice-multi",
            bob, Map.of("paymentMethod", "BANK_TRANSFER"), "bob-multi");
        assertThat(responses).hasSize(2).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(201));
        assertThat(stock(1)).isZero();
        assertThat(stock(14)).isZero();
        assertThat(orderCount()).isEqualTo(2);
    }

    @Test
    void databaseConstraintsProtectOrderIdentityPricesAndQuantities() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.add(1, 2, "Paperback");
        long id = created(browser.place(Map.of("paymentMethod", "BANK_TRANSFER"), "constraint-order")).get("orderId").asLong();
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET total_price = -1 WHERE order_id = ?", id))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET status = 'invalid' WHERE order_id = ?", id))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE order_items SET quantity = 0 WHERE order_id = ?", id))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE order_items SET unit_price = -1 WHERE order_id = ?", id))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT line_total FROM order_items WHERE order_id = ?", java.math.BigDecimal.class, id))
            .isEqualByComparingTo("36.00");
    }

    private int stock(long id) {
        return jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = ?", Integer.class, id);
    }

    private int orderCount() { return jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class); }

    private void assertNoOrderAndIntactCart(Browser browser, int expectedStock, int quantity) throws Exception {
        assertThat(orderCount()).isZero();
        assertThat(stock(1)).isEqualTo(expectedStock);
        assertThat(ok(browser.get("/api/cart")).get("totalQuantity").asInt()).isEqualTo(quantity);
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo(code);
    }

    private JsonNode created(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as("response: %s", response.body()).isEqualTo(201);
        return json.readTree(response.body());
    }

    private JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as("response: %s", response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private List<HttpResponse<String>> concurrent(Browser first, Object firstBody, String firstKey,
        Browser second, Object secondBody, String secondKey) throws Exception {
        String firstToken = first.csrf();
        String secondToken = second.csrf();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return first.send("/api/orders", firstBody, firstToken, firstKey);
            });
            var two = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return second.send("/api/orders", secondBody, secondToken, secondKey);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Browser customer(String email) throws Exception {
        Browser browser = new Browser();
        assertThat(browser.post("/api/auth/register", Map.of("username", "Customer", "email", email, "password", "correct-password")).statusCode()).isEqualTo(201);
        ok(browser.post("/api/auth/login", Map.of("email", email, "password", "correct-password")));
        browser.addressId = browser.address("Original shipping", false);
        return browser;
    }

    private Browser login(String email) throws Exception {
        Browser browser = new Browser();
        ok(browser.post("/api/auth/login", Map.of("email", email, "password", "correct-password")));
        return browser;
    }

    private class Browser {
        long addressId;
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        String csrf() throws Exception { return ok(get("/api/auth/csrf")).get("token").asText(); }

        void add(long id, int quantity, String format) throws Exception {
            ok(post("/api/cart/add", Map.of("productId", id, "quantity", quantity, "selectedFormat", format)));
        }

        long address(String line, boolean isDefault) throws Exception {
            return created(post("/api/account/addresses/add", Map.of("fullName", "Alice Example", "addressLine", line, "isDefault", isDefault)))
                .get("addressId").asLong();
        }

        long card(String type, String currency) throws Exception {
            YearMonth future = YearMonth.now(ZoneOffset.UTC).plusYears(2);
            return created(post("/api/account/payment-methods/add", Map.of("type", type, "brand", "visa", "cardholderName", "Alice Example",
                "last4", "0001", "expiryMonth", future.getMonthValue(), "expiryYear", future.getYear(), "currency", currency)))
                .get("paymentMethodId").asLong();
        }

        HttpResponse<String> place(Object body, String key) throws Exception { return send("/api/orders", body, csrf(), key); }
        HttpResponse<String> post(String path, Object body) throws Exception { return send(path, body, csrf(), null); }

        HttpResponse<String> send(String path, Object body, String token, String key) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (token != null) request.header("X-CSRF-TOKEN", token);
            if (key != null) request.header("Idempotency-Key", key);
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
