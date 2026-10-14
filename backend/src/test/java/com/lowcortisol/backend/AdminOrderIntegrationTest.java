package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.adminorder.AdminOrderService;
import com.lowcortisol.backend.adminorder.UpdateOrderStatusRequest;
import com.lowcortisol.backend.catalog.CatalogDemoData;
import jakarta.validation.ConstraintViolationException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminOrderIntegrationTest {
    private static final String API = "/api/admin/orders";
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "admin-order-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> true);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @MockitoSpyBean JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired CatalogDemoData demo;
    @Autowired AdminOrderService orders;

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
    void emptyListAndHistoryUseBoundedPageMetadata() throws Exception {
        Browser admin = admin();
        JsonNode list = ok(admin.get(API));
        assertThat(list.get("items").isEmpty()).isTrue();
        assertThat(list.get("totalItems").asLong()).isZero();
        assertThat(list.get("totalPages").asLong()).isZero();
        assertThat(list.get("pageSize").asInt()).isEqualTo(15);
        Browser customer = customer("alice");
        long id = place(customer, 1);
        JsonNode history = ok(admin.get(API + "/" + id + "/status-history"));
        assertThat(history.get("items").isEmpty()).isTrue();
        assertThat(history.get("totalItems").asLong()).isZero();
        assertThat(history.get("pageSize").asInt()).isEqualTo(15);
    }

    @Test
    void listIncludesAllCustomersWithStablePagingAndCombinedFilters() throws Exception {
        Browser admin = admin();
        customer("alice"); customer("bob");
        long alice = userId("alice@example.com"), bob = userId("bob@example.com");
        long newest = 0;
        for (int i = 0; i < 32; i++) newest = seed(i % 2 == 0 ? alice : bob, i % 3 == 0 ? "completed" : "pending");
        JsonNode first = ok(admin.get(API)), second = ok(admin.get(API + "?page=2")), third = ok(admin.get(API + "?page=3"));
        assertThat(first.get("totalItems").asLong()).isEqualTo(32);
        assertThat(first.get("totalPages").asLong()).isEqualTo(3);
        assertThat(first.get("items").size()).isEqualTo(15);
        assertThat(second.get("items").size()).isEqualTo(15);
        assertThat(third.get("items").size()).isEqualTo(2);
        assertThat(first.at("/items/0/order/orderId").asLong()).isEqualTo(newest);
        assertThat(first.at("/items/14/order/orderId").asLong()).isGreaterThan(second.at("/items/0/order/orderId").asLong());
        JsonNode filtered = ok(admin.get(API + "?status=%20COMPLETED%20&customerId=" + alice + "&q=ALICE"));
        assertThat(filtered.get("totalItems").asLong()).isEqualTo(6);
        for (JsonNode item : filtered.get("items")) {
            assertThat(item.at("/order/status").asText()).isEqualTo("completed");
            assertThat(item.at("/customer/userId").asLong()).isEqualTo(alice);
        }
        assertThat(ok(admin.get(API + "?q=bob%40example.com")).get("totalItems").asLong()).isEqualTo(16);
        assertThat(ok(admin.get(API + "?q=" + newest)).at("/items/0/order/orderId").asLong()).isEqualTo(newest);
        JsonNode beyond = ok(admin.get(API + "?page=2147483647"));
        assertThat(beyond.get("items").isEmpty()).isTrue();
        assertThat(beyond.get("totalItems").asLong()).isEqualTo(32);
        assertThat(ok(admin.get(API + "?customerId=999999")).get("totalItems").asLong()).isZero();
    }

    @Test
    void searchEscapesWildcardsAndSqlText() throws Exception {
        Browser admin = admin(); customer("alice"); customer("bob");
        jdbc.update("UPDATE users SET username = '100%_! Customer' WHERE email = 'alice@example.com'");
        seed(userId("alice@example.com"), "pending"); seed(userId("bob@example.com"), "pending");
        for (String q : List.of("%_!", "100%", "_!")) {
            assertThat(ok(admin.get(API + "?q=" + encode(q))).get("totalItems").asLong()).isEqualTo(1);
        }
        assertThat(ok(admin.get(API + "?q=" + encode("' OR 1=1 --"))).get("totalItems").asLong()).isZero();
    }

    @Test
    void detailsIncludeSafeCustomerContactAndCompleteHistoricalOrder() throws Exception {
        Browser admin = admin(), alice = customer("alice"), bob = customer("bob");
        alice.add(1, 2, "Paperback"); alice.add(14, 1, "Paperback");
        JsonNode original = created(alice.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER")));
        long id = original.get("orderId").asLong();
        jdbc.update("UPDATE users SET username = 'Renamed', phone = '555123' WHERE email = 'alice@example.com'");
        jdbc.update("UPDATE products SET product_name = 'New title', product_price = 999 WHERE product_id = 1");
        jdbc.update("DELETE FROM addresses WHERE user_id = ?", userId("alice@example.com"));
        JsonNode result = ok(admin.get(API + "/" + id));
        assertThat(result.get("order")).isEqualTo(original);
        assertThat(result.at("/customer/username").asText()).isEqualTo("Renamed");
        assertThat(result.at("/customer/phone").asText()).isEqualTo("555123");
        assertThat(result.at("/customer/email").asText()).isEqualTo("alice@example.com");
        assertThat(result.get("version").asLong()).isZero();
        assertThat(result.get("stockReleased").asBoolean()).isFalse();
        assertThat(result.toString()).doesNotContain("passwordHash", "password_hash", "idempotencyKey", "requestFingerprint");
        assertThat(ok(admin.get(API)).at("/items/0")).isEqualTo(result);
        assertError(bob.get("/api/orders/" + id), 404, "ORDER_NOT_FOUND");
        // Admins may shop on the storefront, but the customer endpoint only returns their own orders.
        assertThat(admin.get("/api/orders/" + id).statusCode()).isEqualTo(404);
    }

    @Test
    void completionPreservesPaymentStockAndSnapshotsAndRecordsActorAndReason() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        long id = place(customer, 2);
        jdbc.update("UPDATE orders SET payment_status = 'paid' WHERE order_id = ?", id);
        JsonNode before = ok(customer.get("/api/orders/" + id));
        JsonNode result = ok(admin.send("PUT", statusPath(id), change(" COMPLETED ", 0, " Shipped to customer ")));
        assertThat(result.at("/order/status").asText()).isEqualTo("completed");
        assertThat(result.at("/order/paymentStatus").asText()).isEqualTo("paid");
        assertThat(result.at("/order/items")).isEqualTo(before.get("items"));
        assertThat(result.at("/order/totalPrice")).isEqualTo(before.get("totalPrice"));
        assertThat(result.get("version").asLong()).isEqualTo(1);
        assertThat(result.get("stockReleased").asBoolean()).isFalse();
        assertThat(stock(1)).isEqualTo(72);
        assertThat(count("inventory_adjustments")).isZero();
        JsonNode history = ok(admin.get(API + "/" + id + "/status-history"));
        assertThat(history.get("totalItems").asLong()).isEqualTo(1);
        assertThat(history.at("/items/0/previousStatus").asText()).isEqualTo("pending");
        assertThat(history.at("/items/0/newStatus").asText()).isEqualTo("completed");
        assertThat(history.at("/items/0/reason").asText()).isEqualTo("Shipped to customer");
        assertThat(history.at("/items/0/adminUserId").asLong()).isEqualTo(userId("admin@example.com"));
        assertThat(history.at("/items/0/orderVersion").asLong()).isEqualTo(1);
        assertThat(ok(admin.get(API + "/" + id + "/status-history?page=2")).get("items").isEmpty()).isTrue();
        assertThat(ok(customer.get("/api/account/orders?status=completed")).get("totalItems").asLong()).isEqualTo(1);
    }

    @Test
    void unchangedStatusIsANoOpAndOrdersCanMoveBetweenStatuses() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        long id = place(customer, 1);
        JsonNode before = ok(admin.get(API + "/" + id));
        assertThat(ok(admin.send("PUT", statusPath(id), change("pending", 0, "No change")))).isEqualTo(before);
        assertThat(count("order_status_changes")).isZero();
        JsonNode completed = ok(admin.send("PUT", statusPath(id), change("completed", 0, "Fulfilled")));
        assertThat(ok(admin.send("PUT", statusPath(id), change("completed", 1, "Retry")))).isEqualTo(completed);
        assertError(admin.send("PUT", statusPath(id), change("completed", 0, "Stale retry")), 409, "STALE_ORDER_VERSION");
        // Completed orders can be moved back, e.g. to processing; stock is untouched.
        JsonNode processing = ok(admin.send("PUT", statusPath(id), change("processing", 1, "Reopen")));
        assertThat(processing.at("/order/status").asText()).isEqualTo("processing");
        assertThat(processing.get("version").asLong()).isEqualTo(2);
        assertThat(ok(customer.get("/api/account/orders?status=processing")).get("totalItems").asLong()).isEqualTo(1);
        assertThat(count("order_status_changes")).isEqualTo(2);
        assertThat(stock(1)).isEqualTo(73);
    }

    @Test
    void cancellationAggregatesFormatsRestoresOnceAndInvalidatesStaleInventoryEdits() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        int otherBefore = stock(14);
        customer.add(1, 2, "Paperback"); customer.add(1, 3, "Hardcover"); customer.add(14, 1, "Paperback");
        JsonNode original = created(customer.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER")));
        long id = original.get("orderId").asLong();
        JsonNode cancelled = ok(admin.send("PUT", statusPath(id), change("cancelled", 0, "Customer requested cancellation")));
        assertThat(cancelled.at("/order/status").asText()).isEqualTo("cancelled");
        assertThat(cancelled.get("stockReleased").asBoolean()).isTrue();
        assertThat(cancelled.at("/order/items")).isEqualTo(original.get("items"));
        assertThat(stock(1)).isEqualTo(74);
        assertThat(stock(14)).isEqualTo(otherBefore);
        assertThat(productVersion(1)).isEqualTo(2);
        assertThat(count("inventory_adjustments")).isEqualTo(2);
        JsonNode ledger = ok(admin.get("/api/admin/products/1/stock-history"));
        assertThat(ledger.at("/items/0/quantityBefore").asInt()).isEqualTo(69);
        assertThat(ledger.at("/items/0/quantityAfter").asInt()).isEqualTo(74);
        assertThat(ledger.at("/items/0/orderId").asLong()).isEqualTo(id);
        assertThat(ledger.at("/items/0/delta").asLong()).isEqualTo(5);
        assertThat(ok(customer.get("/api/cart")).get("totalQuantity").asInt()).isZero();
        assertThat(ok(customer.get("/api/account/orders?status=cancelled")).at("/items/0/items")).isEqualTo(original.get("items"));
        assertThat(ok(admin.send("PUT", statusPath(id), change("cancelled", 1, "Retry")))).isEqualTo(cancelled);
        assertError(admin.send("PUT", statusPath(id), change("cancelled", 0, "Stale")), 409, "STALE_ORDER_VERSION");
        assertError(admin.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", 69, "expectedVersion", 1, "reason", "Old form")), 409, "STALE_PRODUCT_VERSION");
        assertThat(stock(1)).isEqualTo(74);
        // Reversing the cancellation takes the books out of stock again.
        JsonNode reopened = ok(admin.send("PUT", statusPath(id), change("completed", 1, "Reopen")));
        assertThat(reopened.at("/order/status").asText()).isEqualTo("completed");
        assertThat(reopened.get("stockReleased").asBoolean()).isFalse();
        assertThat(stock(1)).isEqualTo(69);
        assertThat(stock(14)).isEqualTo(otherBefore - 1);
        assertThat(count("order_status_changes")).isEqualTo(2);
        assertThat(count("inventory_adjustments")).isEqualTo(4);
        assertThat(ok(admin.get("/api/admin/products/1/stock-history")).at("/items/0/delta").asLong()).isEqualTo(-5);
    }

    @Test
    void reversingACancellationFailsWithoutEnoughStock() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        long id = place(customer, 2);
        ok(admin.send("PUT", statusPath(id), change("cancelled", 0, "Cancelled")));
        jdbc.update("UPDATE products SET product_quantity = 1 WHERE product_id = 1");
        assertError(admin.send("PUT", statusPath(id), change("pending", 1, "Reopen")), 409, "INSUFFICIENT_STOCK");
        assertThat(stock(1)).isEqualTo(1);
        assertThat(ok(admin.get(API + "/" + id)).at("/order/status").asText()).isEqualTo("cancelled");
    }

    @Test
    void completingCashOnDeliveryRecordsPayment() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        customer.add(1, 1, "Paperback");
        long id = created(customer.send("POST", "/api/orders", Map.of("paymentMethod", "CASH_ON_DELIVERY"))).get("orderId").asLong();
        JsonNode completed = ok(admin.send("PUT", statusPath(id), change("completed", 0, "Delivered and paid")));
        assertThat(completed.at("/order/paymentStatus").asText()).isEqualTo("paid");
    }

    @Test
    void cancellationPreservesPaidStateWithoutAutomaticallyRefunding() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        long id = place(customer, 1);
        jdbc.update("UPDATE orders SET payment_status = 'paid' WHERE order_id = ?", id);
        assertThat(ok(admin.send("PUT", statusPath(id), change("cancelled", 0, "Cancelled"))).at("/order/paymentStatus").asText()).isEqualTo("paid");
        assertThat(stock(1)).isEqualTo(74);
    }

    @Test
    void deletedProductsAreSkippedWhileSnapshotsAndOtherStockRemainIntact() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        int other = stock(14);
        customer.add(1, 2, "Paperback"); customer.add(14, 1, "Paperback");
        JsonNode original = created(customer.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER")));
        long id = original.get("orderId").asLong();
        // Admins cannot delete ordered products any more; simulate a product removed directly in the database.
        assertThat(admin.send("DELETE", "/api/admin/products/1?expectedVersion=1", Map.of()).statusCode()).isEqualTo(409);
        jdbc.update("DELETE FROM products WHERE product_id = 1");
        // Even reusing the source ID must not revive a deleted order-item reference from its snapshot.
        jdbc.update("INSERT INTO products (product_id, category_id, product_name, product_price, product_quantity) OVERRIDING SYSTEM VALUE VALUES (1, 1, 'Replacement', 10, 20)");
        JsonNode result = ok(admin.send("PUT", statusPath(id), change("cancelled", 0, "Source product removed")));
        assertThat(result.at("/order/items")).isEqualTo(original.get("items"));
        assertThat(result.get("stockReleased").asBoolean()).isTrue();
        assertThat(stock(1)).isEqualTo(20);
        assertThat(stock(14)).isEqualTo(other);
        assertThat(count("inventory_adjustments")).isEqualTo(1);
    }

    @Test
    void stockOverflowRollsBackEveryProductAndTheOrder() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        customer.add(1, 1, "Paperback"); customer.add(14, 1, "Paperback");
        long id = created(customer.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER"))).get("orderId").asLong();
        int first = stock(1);
        jdbc.update("UPDATE products SET product_quantity = ? WHERE product_id = 14", Integer.MAX_VALUE);
        assertError(admin.send("PUT", statusPath(id), change("cancelled", 0, "Restore")), 409, "STOCK_RESTORE_OVERFLOW");
        assertThat(stock(1)).isEqualTo(first);
        assertThat(stock(14)).isEqualTo(Integer.MAX_VALUE);
        assertThat(productVersion(1)).isEqualTo(1);
        assertThat(ok(admin.get(API + "/" + id)).get("version").asLong()).isZero();
        assertThat(count("inventory_adjustments")).isZero();
        assertThat(count("order_status_changes")).isZero();
    }

    @Test
    void auditStorageFailureRollsBackRestockingAndAllowsRetry() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        long id = place(customer, 2);
        jdbc.execute("CREATE FUNCTION reject_order_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'simulated audit failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_order_audit BEFORE INSERT ON order_status_changes FOR EACH ROW EXECUTE FUNCTION reject_order_audit()");
        try {
            assertError(admin.send("PUT", statusPath(id), change("cancelled", 0, "Cancel")), 500, "DATABASE_ERROR");
            assertThat(stock(1)).isEqualTo(72);
            assertThat(productVersion(1)).isEqualTo(1);
            JsonNode order = ok(admin.get(API + "/" + id));
            assertThat(order.at("/order/status").asText()).isEqualTo("pending");
            assertThat(order.get("version").asLong()).isZero();
            assertThat(order.get("stockReleased").asBoolean()).isFalse();
            assertThat(count("inventory_adjustments")).isZero();
            assertThat(count("order_status_changes")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_order_audit ON order_status_changes");
            jdbc.execute("DROP FUNCTION reject_order_audit()");
        }
        ok(admin.send("PUT", statusPath(id), change("cancelled", 0, "Retry")));
        assertThat(stock(1)).isEqualTo(74);
    }

    @Test
    void existingCancelledOrdersAreNotRestockedByRetries() throws Exception {
        Browser admin = admin(); customer("alice");
        long id = seed(userId("alice@example.com"), "cancelled");
        JsonNode before = ok(admin.get(API + "/" + id));
        assertThat(ok(admin.send("PUT", statusPath(id), change("cancelled", 0, "Legacy retry")))).isEqualTo(before);
        assertThat(stock(1)).isEqualTo(74);
        assertThat(count("inventory_adjustments")).isZero();
    }

    @Test
    void invalidBodiesIdsAndFiltersCannotChangeOrders() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        long id = place(customer, 1);
        for (Map<String, ?> body : List.of(Map.of("status", "completed"), Map.of("status", "completed", "expectedVersion", 0),
            change("paid", 0, "Reason"), change("", 0, "Reason"), change("completed", -1, "Reason"),
            change("completed", 0, " "), change("completed", 0, "x".repeat(501)),
            Map.of("status", "completed", "expectedVersion", 0.5, "reason", "Invalid version"))) {
            assertThat(admin.send("PUT", statusPath(id), body).statusCode()).as(body.toString()).isEqualTo(400);
        }
        for (String field : List.of("userId", "adminUserId", "paymentStatus", "totalPrice", "stockReleased", "items")) {
            Map<String, Object> body = new HashMap<>(change("completed", 0, "Reason")); body.put(field, 1);
            assertThat(admin.send("PUT", statusPath(id), body).statusCode()).isEqualTo(400);
        }
        for (String invalid : List.of("0", "-1", "abc", "1.5", "9223372036854775808")) {
            assertThat(admin.get(API + "/" + invalid).statusCode()).isEqualTo(400);
            assertThat(admin.get(API + "?customerId=" + invalid).statusCode()).isEqualTo(400);
            assertThat(admin.send("PUT", API + "/" + invalid + "/status", change("completed", 0, "Reason")).statusCode()).isEqualTo(400);
        }
        for (String invalid : List.of("0", "-1", "abc", "1.5", "2147483648")) {
            assertThat(admin.get(API + "?page=" + invalid).statusCode()).isEqualTo(400);
            assertThat(admin.get(API + "/" + id + "/status-history?page=" + invalid).statusCode()).isEqualTo(400);
        }
        assertThat(admin.get(API + "?status=paid").statusCode()).isEqualTo(400);
        assertThat(admin.get(API + "?q=" + "x".repeat(256)).statusCode()).isEqualTo(400);
        assertError(admin.get(API + "/999999"), 404, "ADMIN_ORDER_NOT_FOUND");
        assertError(admin.get(API + "/999999/status-history"), 404, "ADMIN_ORDER_NOT_FOUND");
        assertError(admin.send("PUT", API + "/999999/status", change("completed", 0, "Reason")), 404, "ADMIN_ORDER_NOT_FOUND");
        assertThat(ok(admin.get(API + "/" + id)).get("version").asLong()).isZero();
        assertThat(stock(1)).isEqualTo(73);
    }

    @Test
    void authorizationAndCsrfProtectEveryEndpoint() throws Exception {
        Browser admin = admin(), guest = new Browser(), customer = customer("alice");
        long id = place(customer, 1);
        for (String path : List.of(API, API + "/" + id, API + "/" + id + "/status-history")) {
            assertThat(guest.get(path).statusCode()).isEqualTo(401);
            assertThat(customer.get(path).statusCode()).isEqualTo(403);
        }
        assertThat(guest.send("PUT", statusPath(id), change("cancelled", 0, "Reason")).statusCode()).isEqualTo(401);
        assertThat(customer.send("PUT", statusPath(id), change("cancelled", 0, "Reason")).statusCode()).isEqualTo(403);
        assertThat(admin.send("PUT", statusPath(id), change("cancelled", 0, "Reason"), null).statusCode()).isEqualTo(403);
        assertThat(stock(1)).isEqualTo(73);
    }

    @Test
    void serviceChecksRoleIdentityAndBeanValidation() throws Exception {
        admin();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@example.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            assertThat(orders.list("admin@example.com", "1", null, "", null).totalItems()).isZero();
            assertThatThrownBy(() -> orders.list("other@example.com", "1", null, "", null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> orders.updateStatus("admin@example.com", 1, new UpdateOrderStatusRequest("completed", null, "Reason")))
                .isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> orders.updateStatus("admin@example.com", 1, null)).isInstanceOf(ConstraintViolationException.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
            assertThatThrownBy(() -> orders.get("alice@example.com", 1)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> orders.updateStatus("alice@example.com", 1, new UpdateOrderStatusRequest("cancelled", 0L, "Reason")))
                .isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void concurrentCompletionAndCancellationChooseOneVersion() throws Exception {
        Browser first = admin(), second = adminLogin(), customer = customer("alice");
        long id = place(customer, 1);
        var result = concurrent(first, "PUT", statusPath(id), change("completed", 0, "Fulfilled"),
            second, "PUT", statusPath(id), change("cancelled", 0, "Cancel"));
        assertThat(result.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200, 409);
        JsonNode order = ok(first.get(API + "/" + id));
        boolean cancelled = order.at("/order/status").asText().equals("cancelled");
        assertThat(stock(1)).isEqualTo(cancelled ? 74 : 73);
        assertThat(count("inventory_adjustments")).isEqualTo(cancelled ? 1 : 0);
        assertThat(count("order_status_changes")).isEqualTo(1);
        assertThat(order.get("version").asLong()).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateCancellationRestoresOnlyOnce() throws Exception {
        Browser first = admin(), second = adminLogin(), customer = customer("alice");
        long id = place(customer, 3);
        var result = concurrent(first, "PUT", statusPath(id), change("cancelled", 0, "Cancel"),
            second, "PUT", statusPath(id), change("cancelled", 0, "Cancel"));
        assertThat(result.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200, 409);
        assertThat(stock(1)).isEqualTo(74);
        assertThat(productVersion(1)).isEqualTo(2);
        assertThat(count("inventory_adjustments")).isEqualTo(1);
    }

    @Test
    void concurrentCancellationsOfDifferentOrdersRestoreAggregateStock() throws Exception {
        Browser first = admin(), second = adminLogin(), alice = customer("alice"), bob = customer("bob");
        int other = stock(14);
        alice.add(1, 2, "Paperback"); alice.add(14, 1, "Paperback");
        bob.add(14, 2, "Paperback"); bob.add(1, 3, "Paperback");
        long a = created(alice.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER"))).get("orderId").asLong();
        long b = created(bob.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER"))).get("orderId").asLong();
        var result = concurrent(first, "PUT", statusPath(a), change("cancelled", 0, "Cancel Alice"),
            second, "PUT", statusPath(b), change("cancelled", 0, "Cancel Bob"));
        for (var response : result) ok(response);
        assertThat(stock(1)).isEqualTo(74);
        assertThat(stock(14)).isEqualTo(other);
        assertThat(productVersion(1)).isEqualTo(4);
        assertThat(count("inventory_adjustments")).isEqualTo(4);
    }

    @Test
    void concurrentCancellationAndCheckoutSerializeStockWithoutDeadlock() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        long id = place(customer, 2);
        customer.add(1, 3, "Paperback");
        var result = concurrent(admin, "PUT", statusPath(id), change("cancelled", 0, "Cancel"),
            customer, "POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER"));
        ok(result.get(0)); created(result.get(1));
        assertThat(stock(1)).isEqualTo(71);
        assertThat(productVersion(1)).isEqualTo(3);
        assertThat(ok(customer.get("/api/cart")).get("totalQuantity").asInt()).isZero();
    }

    @Test
    void concurrentProductDeletionAndCancellationPreserveSnapshotsWithoutDeadlock() throws Exception {
        Browser first = admin(), second = adminLogin(), customer = customer("alice");
        long id = place(customer, 1);
        JsonNode original = ok(customer.get("/api/orders/" + id));
        var result = concurrent(first, "PUT", statusPath(id), change("cancelled", 0, "Cancel"),
            second, "DELETE", "/api/admin/products/1?expectedVersion=1", Map.of());
        JsonNode cancelled = ok(result.get(0));
        assertThat(result.get(1).statusCode()).as(result.get(1).body()).isIn(204, 409);
        assertThat(cancelled.at("/order/items")).isEqualTo(original.get("items"));
        assertThat(cancelled.at("/order/status").asText()).isEqualTo("cancelled");
        if (result.get(1).statusCode() == 409) assertThat(stock(1)).isEqualTo(74);
        else assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE product_id = 1", Long.class)).isZero();
    }

    @Test
    void countAndPageContentsUseOneSnapshotDuringConcurrentCreation() throws Exception {
        Browser admin = admin(); customer("alice");
        long customerId = userId("alice@example.com");
        long first = seed(customerId, "pending"), second = seed(customerId, "pending");
        AtomicBoolean inserted = new AtomicBoolean();
        doAnswer(invocation -> {
            Object count = invocation.callRealMethod();
            if (inserted.compareAndSet(false, true)) {
                // Commit between count and page queries on a separate connection outside the read transaction.
                CompletableFuture.runAsync(() -> seed(customerId, "pending")).get(10, TimeUnit.SECONDS);
            }
            return count;
        }).when(jdbc).queryForObject(eq("SELECT count(*) FROM orders o JOIN users u ON u.user_id = o.user_id WHERE TRUE "),
            eq(Long.class), any(Object[].class));
        try {
            JsonNode page = ok(admin.get(API));
            assertThat(inserted).isTrue();
            assertThat(page.get("totalItems").asLong()).isEqualTo(2);
            assertThat(page.get("items").size()).isEqualTo(2);
            assertThat(page.at("/items/0/order/orderId").asLong()).isEqualTo(second);
            assertThat(page.at("/items/1/order/orderId").asLong()).isEqualTo(first);
        } finally { reset(jdbc); }
        assertThat(ok(admin.get(API)).get("totalItems").asLong()).isEqualTo(3);
    }

    @Test
    void deletingAccountsRetainsStockAndClearsAuditReferencesSafely() throws Exception {
        Browser admin = admin(), customer = customer("alice");
        long id = place(customer, 2);
        ok(admin.send("PUT", statusPath(id), change("cancelled", 0, "Cancel")));
        jdbc.update("DELETE FROM users WHERE email = 'admin@example.com'");
        assertThat(jdbc.queryForObject("SELECT admin_user_id FROM order_status_changes WHERE order_id = ?", Long.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT admin_user_id FROM inventory_adjustments WHERE order_id = ?", Long.class, id)).isNull();
        jdbc.update("DELETE FROM users WHERE email = 'alice@example.com'");
        assertThat(count("orders")).isZero();
        assertThat(count("order_status_changes")).isZero();
        assertThat(jdbc.queryForObject("SELECT order_id FROM inventory_adjustments", Long.class)).isNull();
        assertThat(stock(1)).isEqualTo(74);
    }

    private long seed(long customer, String status) {
        long id = jdbc.queryForObject("""
            INSERT INTO orders (user_id, idempotency_key, request_fingerprint, address_snapshot, payment_method,
                total_items, total_quantity, total_price, status, created_at)
            VALUES (?, ?, ?, '{"fullName":"Original","addressLine":"Street"}'::jsonb, 'BANK_TRANSFER', 1, 1, 18, ?, '2026-01-01T00:00:00Z')
            RETURNING order_id
            """, Long.class, customer, UUID.randomUUID().toString(), "a".repeat(64), status);
        jdbc.update("""
            INSERT INTO order_items (order_id, product_id, product_snapshot, selected_attributes, quantity, unit_price)
            VALUES (?, 1, '{"productId":1,"productName":"Original book"}'::jsonb, '{"format":"Paperback"}'::jsonb, 1, 18)
            """, id);
        return id;
    }

    private long place(Browser customer, int quantity) throws Exception {
        customer.add(1, quantity, "Paperback");
        return created(customer.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER"))).get("orderId").asLong();
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String statusPath(long id) { return API + "/" + id + "/status"; }
    private Map<String, Object> change(String status, long version, String reason) {
        return Map.of("status", status, "expectedVersion", version, "reason", reason);
    }
    private long userId(String email) { return jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email); }
    private int stock(long id) { return jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = ?", Integer.class, id); }
    private long productVersion(long id) { return jdbc.queryForObject("SELECT version FROM products WHERE product_id = ?", Long.class, id); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).as("response: %s", response.body()).isEqualTo(status);
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo(code);
    }
    private JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as("response: %s", response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }
    private JsonNode created(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as("response: %s", response.body()).isEqualTo(201);
        return json.readTree(response.body());
    }
    private Browser admin() throws Exception {
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES ('Admin', 'admin@example.com', ?, 'admin')", encoder.encode("correct-password"));
        return adminLogin();
    }
    private Browser adminLogin() throws Exception {
        Browser browser = new Browser();
        ok(browser.send("POST", "/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "correct-password")));
        return browser;
    }
    private Browser customer(String name) throws Exception {
        Browser browser = new Browser();
        created(browser.send("POST", "/api/auth/register", Map.of("username", name, "email", name + "@example.com", "password", "correct-password")));
        ok(browser.send("POST", "/api/auth/login", Map.of("email", name + "@example.com", "password", "correct-password")));
        created(browser.send("POST", "/api/account/addresses/add", Map.of("fullName", name, "addressLine", "Street")));
        return browser;
    }
    private List<HttpResponse<String>> concurrent(Browser one, String methodOne, String pathOne, Object bodyOne,
        Browser two, String methodTwo, String pathTwo, Object bodyTwo) throws Exception {
        String firstToken = one.csrf(), secondToken = two.csrf();
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return one.send(methodOne, pathOne, bodyOne, firstToken);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return two.send(methodTwo, pathTwo, bodyTwo, secondToken);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    private class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        String csrf() throws Exception { return ok(get("/api/auth/csrf")).get("token").asText(); }
        HttpResponse<String> send(String method, String path, Object body) throws Exception { return send(method, path, body, csrf()); }
        HttpResponse<String> send(String method, String path, Object body, String token) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (token != null) request.header("X-CSRF-TOKEN", token);
            if (path.equals("/api/orders")) request.header("Idempotency-Key", UUID.randomUUID().toString());
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        void add(long id, int quantity, String format) throws Exception {
            ok(send("POST", "/api/cart/add", Map.of("productId", id, "quantity", quantity, "selectedFormat", format)));
        }
    }
}
