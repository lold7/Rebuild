package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.catalog.CatalogDemoData;
import com.lowcortisol.backend.order.OrderService;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderHistoryIntegrationTest {
    private static final String API = "/api/account/orders";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "history-test-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> true);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @MockitoSpyBean JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired CatalogDemoData demo;
    @Autowired PasswordEncoder encoder;
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
    void newCustomerHasAnEmptyHistoryWithPageMetadata() throws Exception {
        JsonNode history = ok(customer("alice@example.com").get(API));
        assertThat(history.get("items").size()).isZero();
        assertThat(history.get("page").asInt()).isEqualTo(1);
        assertThat(history.get("pageSize").asInt()).isEqualTo(15);
        assertThat(history.get("totalItems").asLong()).isZero();
        assertThat(history.get("totalPages").asLong()).isZero();
    }

    @Test
    void pagesAreBoundedStableNewestFirstAndCountOnlyOwnedOrders() throws Exception {
        Browser alice = customer("alice@example.com");
        Browser bob = customer("bob@example.com");
        List<Long> ids = seed(alice.userId, 32, "pending");
        seed(bob.userId, 20, "pending");
        Collections.reverse(ids);
        assertThat(ids(ok(alice.get(API)))).containsExactlyElementsOf(ids.subList(0, 15));
        JsonNode second = ok(alice.get(API + "?page=2"));
        assertThat(ids(second)).containsExactlyElementsOf(ids.subList(15, 30));
        assertThat(second.get("page").asInt()).isEqualTo(2);
        assertThat(second.get("pageSize").asInt()).isEqualTo(15);
        assertThat(second.get("totalItems").asLong()).isEqualTo(32);
        assertThat(second.get("totalPages").asLong()).isEqualTo(3);
        assertThat(ids(ok(alice.get(API + "?page=3")))).containsExactlyElementsOf(ids.subList(30, 32));
        assertThat(ok(alice.get(API + "?page=4")).get("items").size()).isZero();
        assertThat(ok(alice.get(API + "?page=2147483647")).get("items").size()).isZero();
        assertThat(ok(bob.get(API)).get("totalItems").asInt()).isEqualTo(20);
    }

    @Test
    void timestampOrderingTakesPriorityOverOrderIdAndTiesUseDescendingId() throws Exception {
        Browser browser = customer("alice@example.com");
        List<Long> orders = seed(browser.userId, 3, "pending");
        jdbc.update("UPDATE orders SET created_at = '2026-02-01T00:00:00Z' WHERE order_id = ?", orders.get(0));
        assertThat(ids(ok(browser.get(API)))).containsExactly(orders.get(0), orders.get(2), orders.get(1));
    }

    @Test
    void statusFiltersAreNormalizedPaginatedAndScopedToTheCustomer() throws Exception {
        Browser alice = customer("alice@example.com");
        Browser bob = customer("bob@example.com");
        seed(alice.userId, 17, "completed");
        seed(alice.userId, 2, "pending");
        seed(alice.userId, 1, "cancelled");
        seed(bob.userId, 10, "completed");
        JsonNode completed = ok(alice.get(API + "?status=%20COMPLETED%20"));
        assertThat(completed.get("items").size()).isEqualTo(15);
        assertThat(completed.get("totalItems").asInt()).isEqualTo(17);
        assertThat(completed.get("totalPages").asInt()).isEqualTo(2);
        completed.get("items").forEach(order -> assertThat(order.get("status").asText()).isEqualTo("completed"));
        assertThat(ok(alice.get(API + "?status=completed&page=2")).get("items").size()).isEqualTo(2);
        assertThat(ok(alice.get(API + "?status=pending")).get("totalItems").asInt()).isEqualTo(2);
        assertThat(ok(alice.get(API + "?status=cancelled")).get("totalItems").asInt()).isEqualTo(1);
        assertThat(ok(alice.get(API)).get("totalItems").asInt()).isEqualTo(20);
        jdbc.update("UPDATE orders SET status = 'completed' WHERE user_id = ? AND status = 'pending'", alice.userId);
        assertThat(ok(alice.get(API + "?status=pending")).get("totalPages").asInt()).isZero();
    }

    @Test
    void invalidPagesStatusesAndInjectionAttemptsAreRejected() throws Exception {
        Browser browser = customer("alice@example.com");
        seed(browser.userId, 1, "pending");
        for (String page : List.of("0", "-1", "1.5", "abc", "2147483648", "%2B1")) {
            HttpResponse<String> response = browser.get(API + "?page=" + page);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("INVALID_ORDER_REQUEST");
        }
        for (String status : List.of("", "all", "paid", "unknown", "pending%27%20OR%201%3D1--")) {
            HttpResponse<String> response = browser.get(API + "?status=" + status);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("INVALID_ORDER_REQUEST");
        }
        assertThat(ok(browser.get(API)).get("totalItems").asInt()).isEqualTo(1);
    }

    @Test
    void guestsAndAdminsCannotReadCustomerHistoryAndIdentityParametersCannotChangeOwner() throws Exception {
        assertThat(new Browser().get(API).statusCode()).isEqualTo(401);
        Browser alice = customer("alice@example.com");
        Browser bob = customer("bob@example.com");
        List<Long> aliceOrders = seed(alice.userId, 1, "pending");
        seed(bob.userId, 3, "pending");
        JsonNode attempted = ok(alice.get(API + "?userId=" + bob.userId + "&email=bob@example.com"));
        assertThat(ids(attempted)).containsExactlyElementsOf(aliceOrders);
        assertThat(attempted.get("totalItems").asInt()).isEqualTo(1);
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, 'admin')",
            "Admin", "admin@example.com", encoder.encode("correct-password"));
        Browser admin = new Browser();
        ok(admin.post("/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "correct-password")));
        // Admins see only their own storefront orders, never a customer's history.
        JsonNode adminHistory = ok(admin.get(API));
        assertThat(adminHistory.get("totalItems").asInt()).isZero();
        assertThat(ok(admin.get(API + "?status=completed")).get("totalItems").asInt()).isZero();
    }

    @Test
    void fullOrderItemsAreGroupedCorrectlyAndMatchTheOwnedDetailEndpoint() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.add(1, 2, "Paperback");
        browser.add(1, 1, "Hardcover");
        browser.add(14, 1, "Paperback");
        JsonNode first = browser.place(Map.of("paymentMethod", "BANK_TRANSFER"));
        browser.add(25, 1, "Paperback");
        JsonNode second = browser.place(Map.of("paymentMethod", "DIGITAL_WALLET"));
        JsonNode history = ok(browser.get(API));
        assertThat(history.get("items").get(0)).isEqualTo(second);
        assertThat(history.get("items").get(1)).isEqualTo(first);
        assertThat(first.get("items").size()).isEqualTo(3);
        assertThat(second.get("items").size()).isEqualTo(1);
        for (JsonNode order : history.get("items")) {
            assertThat(ok(browser.get("/api/orders/" + order.get("orderId").asLong()))).isEqualTo(order);
            assertThat(order.has("userId")).isFalse();
            assertThat(order.has("idempotencyKey")).isFalse();
            assertThat(order.has("requestFingerprint")).isFalse();
            assertThat(order.get("address").get("addressLine").asText()).isEqualTo("Original shipping");
            assertThat(order.get("items").get(0).get("product").get("productName").asText()).isNotBlank();
            assertThat(order.get("items").get(0).get("selectedAttributes").get("format").asText()).isNotBlank();
        }
    }

    @Test
    void historyRetainsShippingProductAndMaskedCardSnapshotsAfterSourceDeletion() throws Exception {
        Browser browser = customer("alice@example.com");
        long card = browser.card();
        browser.card();
        browser.add(1, 2, "Paperback");
        JsonNode original = browser.place(Map.of("paymentMethod", "CREDIT_CARD", "savedPaymentMethodId", card));
        jdbc.update("UPDATE products SET product_price = 99, product_name = 'Changed title' WHERE product_id = 1");
        ok(browser.post("/api/account/addresses/delete/" + browser.addressId, Map.of()));
        ok(browser.post("/api/account/payment-methods/delete/" + card, Map.of()));
        jdbc.update("DELETE FROM products WHERE product_id = 1");
        JsonNode stored = ok(browser.get(API)).get("items").get(0);
        assertThat(stored).isEqualTo(original);
        assertThat(stored.get("totalPrice").decimalValue()).isEqualByComparingTo("36.00");
        assertThat(stored.get("savedPaymentMethod").get("last4").asText()).isEqualTo("0001");
        assertThat(stored.get("savedPaymentMethod").has("cvv")).isFalse();
        assertThat(stored.get("savedPaymentMethod").has("cardNumber")).isFalse();
    }

    @Test
    void historyReadsDoNotChangeCartStockOrdersOrPaymentState() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.add(1, 1, "Paperback");
        JsonNode order = browser.place(Map.of("paymentMethod", "BANK_TRANSFER"));
        browser.add(1, 2, "Hardcover");
        int stock = jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = 1", Integer.class);
        JsonNode cart = ok(browser.get("/api/cart"));
        ok(browser.get(API));
        ok(browser.get(API + "?status=pending"));
        assertThat(ok(browser.get("/api/cart"))).isEqualTo(cart);
        assertThat(jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = 1", Integer.class)).isEqualTo(stock);
        assertThat(ok(browser.get("/api/orders/" + order.get("orderId").asLong()))).isEqualTo(order);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void historySurvivesLogoutEmailAndPasswordChanges() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.add(1, 1, "Paperback");
        browser.place(Map.of("paymentMethod", "BANK_TRANSFER"));
        JsonNode original = ok(browser.get(API));
        assertThat(browser.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
        ok(browser.post("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        assertThat(ok(browser.get(API))).isEqualTo(original);
        ok(browser.post("/api/account/profile", Map.of("username", "Alice", "email", "changed@example.com")));
        ok(browser.post("/api/auth/login", Map.of("email", "changed@example.com", "password", "correct-password")));
        ok(browser.post("/api/account/password", Map.of("currentPassword", "correct-password", "newPassword", "changed-password", "confirmPassword", "changed-password")));
        ok(browser.post("/api/auth/login", Map.of("email", "changed@example.com", "password", "changed-password")));
        assertThat(ok(browser.get(API))).isEqualTo(original);
    }

    @Test
    void serviceAuthorizationBindsHistoryToTheCustomerIdentity() throws Exception {
        customer("alice@example.com");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        try {
            assertThat(orders.history("alice@example.com", "1", null).totalItems()).isZero();
            assertThatThrownBy(() -> orders.history("other@example.com", "1", null)).isInstanceOf(AccessDeniedException.class);
            // Admins may shop on the storefront, so only roles other than CUSTOMER/ADMIN are rejected.
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_GUEST"))));
            assertThatThrownBy(() -> orders.history("alice@example.com", "1", null)).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void countAndContentsUseOneSnapshotDuringConcurrentOrderCreation() throws Exception {
        Browser browser = customer("alice@example.com");
        List<Long> original = seed(browser.userId, 2, "pending");
        AtomicBoolean inserted = new AtomicBoolean();
        doAnswer(invocation -> {
            Object count = invocation.callRealMethod();
            if (inserted.compareAndSet(false, true)) {
                // A different connection commits an order after the history count, before its page queries.
                CompletableFuture.runAsync(() -> seedOne(new JdbcTemplate(jdbc.getDataSource()), browser.userId, "pending"))
                    .get(10, TimeUnit.SECONDS);
            }
            return count;
        }).when(jdbc).queryForObject(eq("SELECT count(*) FROM orders WHERE user_id = ?"), eq(Long.class), anyLong());
        try {
            JsonNode history = ok(browser.get(API));
            assertThat(inserted).isTrue();
            assertThat(history.get("totalItems").asInt()).isEqualTo(2);
            Collections.reverse(original);
            assertThat(ids(history)).containsExactlyElementsOf(original);
        } finally {
            reset(jdbc);
        }
        assertThat(ok(browser.get(API)).get("totalItems").asInt()).isEqualTo(3);
    }

    private List<Long> seed(long userId, int count, String status) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) ids.add(seedOne(jdbc, userId, status));
        return ids;
    }

    private long seedOne(JdbcTemplate connection, long userId, String status) {
        long id = connection.queryForObject("""
            INSERT INTO orders (user_id, idempotency_key, request_fingerprint, address_snapshot, payment_method,
                total_items, total_quantity, total_price, status, created_at)
            VALUES (?, ?, ?, '{"fullName":"Customer","addressLine":"Saved shipping"}'::jsonb, 'BANK_TRANSFER',
                1, 1, 18.00, ?, '2026-01-01T00:00:00Z') RETURNING order_id
            """, Long.class, userId, UUID.randomUUID().toString(), "a".repeat(64), status);
        connection.update("""
            INSERT INTO order_items (order_id, product_id, product_snapshot, selected_attributes, quantity, unit_price)
            VALUES (?, 1, '{"productId":1,"productName":"Saved book","productImages":["book.jpg"]}'::jsonb,
                '{"format":"Paperback"}'::jsonb, 1, 18.00)
            """, id);
        return id;
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(order -> ids.add(order.get("orderId").asLong()));
        return ids;
    }

    private JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as("response: %s", response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private JsonNode created(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as("response: %s", response.body()).isEqualTo(201);
        return json.readTree(response.body());
    }

    private Browser customer(String email) throws Exception {
        Browser browser = new Browser();
        assertThat(browser.post("/api/auth/register", Map.of("username", "Customer", "email", email, "password", "correct-password")).statusCode()).isEqualTo(201);
        JsonNode profile = ok(browser.post("/api/auth/login", Map.of("email", email, "password", "correct-password")));
        browser.userId = profile.get("userId").asLong();
        browser.addressId = created(browser.post("/api/account/addresses/add", Map.of("fullName", "Alice", "addressLine", "Original shipping")))
            .get("addressId").asLong();
        return browser;
    }

    private class Browser {
        long userId;
        long addressId;
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, Object body) throws Exception { return send(path, body, null); }

        HttpResponse<String> send(String path, Object body, String key) throws Exception {
            String token = ok(get("/api/auth/csrf")).get("token").asText();
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", token).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (key != null) request.header("Idempotency-Key", key);
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        void add(long productId, int quantity, String format) throws Exception {
            ok(post("/api/cart/add", Map.of("productId", productId, "quantity", quantity, "selectedFormat", format)));
        }

        JsonNode place(Object body) throws Exception { return created(send("/api/orders", body, UUID.randomUUID().toString())); }

        long card() throws Exception {
            YearMonth expiry = YearMonth.now(ZoneOffset.UTC).plusYears(2);
            return created(post("/api/account/payment-methods/add", Map.of("type", "CREDIT", "brand", "visa", "cardholderName", "Alice",
                "last4", "0001", "expiryMonth", expiry.getMonthValue(), "expiryYear", expiry.getYear())))
                .get("paymentMethodId").asLong();
        }
    }
}
