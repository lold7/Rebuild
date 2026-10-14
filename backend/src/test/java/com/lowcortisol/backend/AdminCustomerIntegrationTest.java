package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.admincustomer.AdminCustomerService;
import com.lowcortisol.backend.catalog.CatalogDemoData;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCustomerIntegrationTest {
    private static final String API = "/api/admin/customers";
    private static final String COUNT = """
        SELECT count(*) FROM users u LEFT JOIN admin_customer_order_statistics s ON s.user_id = u.user_id
        WHERE u.role = 'customer'
        """;
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "admin-customer-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> true);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @MockitoSpyBean JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired CatalogDemoData demo;
    @Autowired AdminCustomerService customers;

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
    void emptyReportExcludesAdminsAndReturnsZeroSummary() throws Exception {
        Browser admin = admin();
        JsonNode page = ok(admin.get(API)), summary = ok(admin.get(API + "/summary"));
        assertThat(page.get("items").isEmpty()).isTrue();
        assertThat(page.get("page").asInt()).isEqualTo(1);
        assertThat(page.get("pageSize").asInt()).isEqualTo(15);
        assertThat(page.get("totalItems").asLong()).isZero();
        assertThat(page.get("totalPages").asLong()).isZero();
        for (String field : List.of("totalCustomers", "customersWithOrders", "customersWithoutOrders", "totalOrders",
            "pendingOrders", "completedOrders", "cancelledOrders", "orderValue", "totalSpent")) assertThat(summary.get(field).decimalValue()).isEqualByComparingTo("0");
        assertThat(summary.get("currency").asText()).isEqualTo("USD");
        assertError(admin.get(API + "/" + userId("admin@example.com")), 404, "ADMIN_CUSTOMER_NOT_FOUND");
    }

    @Test
    void customersWithoutOrdersHaveSafeContactFieldsZeroMetricsAndNullLastOrder() throws Exception {
        Browser admin = admin(); long id = customer("alice");
        jdbc.update("UPDATE users SET phone = '555123' WHERE user_id = ?", id);
        JsonNode detail = ok(admin.get(API + "/" + id));
        assertThat(detail.get("userId").asLong()).isEqualTo(id);
        assertThat(detail.get("username").asText()).isEqualTo("alice");
        assertThat(detail.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(detail.get("phone").asText()).isEqualTo("555123");
        assertThat(detail.get("createdAt").asText()).isNotBlank();
        assertThat(detail.get("updatedAt").asText()).isNotBlank();
        assertThat(detail.get("orderCount").asLong()).isZero();
        assertThat(detail.get("lastOrderAt").isNull()).isTrue();
        assertThat(detail.get("orderValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(detail.get("totalSpent").decimalValue()).isEqualByComparingTo("0");
        assertThat(detail.toString()).doesNotContain("password", "role", "remember", "token", "address", "paymentMethod");
        assertThat(ok(admin.get(API)).at("/items/0")).isEqualTo(detail);
        assertThat(ok(admin.get(API + "/summary")).get("customersWithoutOrders").asLong()).isEqualTo(1);
    }

    @Test
    void statusCountsAndPaymentTotalsUseHeadersWithoutMultiplyingItemLines() throws Exception {
        Browser admin = admin(); long alice = customer("alice"), bob = customer("bob");
        seed(alice, "pending", "unpaid", "10.01"); seed(alice, "completed", "paid", "20.02");
        seed(alice, "cancelled", "unpaid", "30.03"); seed(alice, "cancelled", "paid", "40.04");
        seed(alice, "completed", "refunded", "50.05"); seed(alice, "pending", "paid", "60.06");
        long last = seed(alice, "cancelled", "refunded", "70.07");
        jdbc.update("UPDATE orders SET created_at = '2026-09-01T00:00:00Z' WHERE order_id = ?", last);
        seed(userId("admin@example.com"), "completed", "paid", "999.99");
        JsonNode detail = ok(admin.get(API + "/" + alice));
        assertThat(detail.get("orderCount").asLong()).isEqualTo(7);
        assertThat(detail.get("pendingOrderCount").asLong()).isEqualTo(2);
        assertThat(detail.get("completedOrderCount").asLong()).isEqualTo(2);
        assertThat(detail.get("cancelledOrderCount").asLong()).isEqualTo(3);
        assertThat(detail.get("orderValue").decimalValue()).isEqualByComparingTo("140.14");
        assertThat(detail.get("totalSpent").decimalValue()).isEqualByComparingTo("120.12");
        assertThat(detail.get("lastOrderAt").asText()).startsWith("2026-09-01T");
        JsonNode summary = ok(admin.get(API + "/summary"));
        assertThat(summary.get("totalCustomers").asLong()).isEqualTo(2);
        assertThat(summary.get("customersWithOrders").asLong()).isEqualTo(1);
        assertThat(summary.get("customersWithoutOrders").asLong()).isEqualTo(1);
        assertThat(summary.get("totalOrders").asLong()).isEqualTo(7);
        assertThat(summary.get("pendingOrders").asLong()).isEqualTo(2);
        assertThat(summary.get("completedOrders").asLong()).isEqualTo(2);
        assertThat(summary.get("cancelledOrders").asLong()).isEqualTo(3);
        assertThat(summary.get("orderValue").decimalValue()).isEqualByComparingTo("140.14");
        assertThat(summary.get("totalSpent").decimalValue()).isEqualByComparingTo("120.12");
        assertThat(ok(admin.get(API + "/" + bob)).get("orderCount").asLong()).isZero();
    }

    @Test
    void moneyAggregatesRetainExactDecimalsAndLargeTotals() throws Exception {
        Browser admin = admin(); long alice = customer("alice"), bob = customer("bob");
        for (int i = 0; i < 3; i++) seed(alice, "completed", "paid", "99999999.99");
        seed(bob, "completed", "paid", "0.10"); seed(bob, "completed", "paid", "0.20");
        assertThat(ok(admin.get(API + "/" + alice)).get("totalSpent").decimalValue()).isEqualByComparingTo("299999999.97");
        assertThat(ok(admin.get(API + "/" + bob)).get("totalSpent").decimalValue()).isEqualByComparingTo("0.30");
        assertThat(ok(admin.get(API + "/summary")).get("totalSpent").decimalValue()).isEqualByComparingTo("300000000.27");
    }

    @Test
    void newestPagingIsBoundedStableAndCountsCustomersOnce() throws Exception {
        Browser admin = admin(); long newest = 0;
        for (int i = 0; i < 32; i++) newest = customer("customer" + i);
        jdbc.update("UPDATE users SET created_at = '2026-01-01T00:00:00Z' WHERE role = 'customer'");
        seed(newest, "pending", "unpaid", "1.00"); seed(newest, "pending", "unpaid", "2.00");
        JsonNode first = ok(admin.get(API)), second = ok(admin.get(API + "?page=2")), third = ok(admin.get(API + "?page=3"));
        assertThat(first.get("totalItems").asLong()).isEqualTo(32);
        assertThat(first.get("totalPages").asLong()).isEqualTo(3);
        assertThat(first.get("items").size()).isEqualTo(15);
        assertThat(second.get("items").size()).isEqualTo(15);
        assertThat(third.get("items").size()).isEqualTo(2);
        assertThat(first.at("/items/0/userId").asLong()).isEqualTo(newest);
        assertThat(first.at("/items/0/orderCount").asLong()).isEqualTo(2);
        assertThat(first.at("/items/14/userId").asLong()).isGreaterThan(second.at("/items/0/userId").asLong());
        JsonNode beyond = ok(admin.get(API + "?page=2147483647"));
        assertThat(beyond.get("items").isEmpty()).isTrue();
        assertThat(beyond.get("totalItems").asLong()).isEqualTo(32);
    }

    @Test
    void sortsByOrderCountAndPaidSpendingWithNewestTieBreakers() throws Exception {
        Browser admin = admin(); long alice = customer("alice"), bob = customer("bob"), carol = customer("carol");
        seed(alice, "pending", "unpaid", "100.00"); seed(alice, "cancelled", "unpaid", "100.00");
        seed(bob, "completed", "paid", "20.00"); seed(carol, "pending", "paid", "20.00");
        jdbc.update("UPDATE users SET created_at = '2026-01-01T00:00:00Z' WHERE role = 'customer'");
        JsonNode orders = ok(admin.get(API + "?sort=%20ORDERS%20")), spent = ok(admin.get(API + "?sort=spent"));
        assertThat(orders.at("/items/0/userId").asLong()).isEqualTo(alice);
        assertThat(orders.at("/items/1/userId").asLong()).isEqualTo(carol);
        assertThat(spent.at("/items/0/userId").asLong()).isEqualTo(carol);
        assertThat(spent.at("/items/1/userId").asLong()).isEqualTo(bob);
        assertThat(spent.at("/items/2/userId").asLong()).isEqualTo(alice);
    }

    @Test
    void searchAndActivityFiltersApplyIdenticallyToListAndSummary() throws Exception {
        Browser admin = admin(); long alice = customer("alice"); customer("bob"); customer("alicia");
        seed(alice, "cancelled", "unpaid", "30.00");
        JsonNode matched = ok(admin.get(API + "?q=%20ALI%20"));
        assertThat(matched.get("totalItems").asLong()).isEqualTo(2);
        assertThat(ok(admin.get(API + "/summary?q=ALI")).get("totalCustomers").asLong()).isEqualTo(2);
        assertThat(ok(admin.get(API + "?q=ali&activity=%20ORDERED%20")).at("/items/0/userId").asLong()).isEqualTo(alice);
        JsonNode ordered = ok(admin.get(API + "/summary?q=ali&activity=ordered"));
        assertThat(ordered.get("totalCustomers").asLong()).isEqualTo(1);
        assertThat(ordered.get("totalOrders").asLong()).isEqualTo(1);
        assertThat(ordered.get("orderValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(ok(admin.get(API + "?q=ali&activity=no-orders")).get("totalItems").asLong()).isEqualTo(1);
        assertThat(ok(admin.get(API + "/summary?activity=no-orders")).get("customersWithoutOrders").asLong()).isEqualTo(2);
        assertThat(ok(admin.get(API + "?q=alice%40example.com")).get("totalItems").asLong()).isEqualTo(1);
        assertThat(ok(admin.get(API + "?q=missing")).get("totalItems").asLong()).isZero();
        assertThat(ok(admin.get(API + "/summary?q=missing")).get("totalCustomers").asLong()).isZero();
    }

    @Test
    void wildcardAndSqlTextSearchesAreLiteral() throws Exception {
        Browser admin = admin(); long alice = customer("alice"); customer("bob");
        jdbc.update("UPDATE users SET username = '100%_! Customer' WHERE user_id = ?", alice);
        for (String query : List.of("%_!", "100%", "_!")) {
            assertThat(ok(admin.get(API + "?q=" + encode(query))).get("totalItems").asLong()).isEqualTo(1);
            assertThat(ok(admin.get(API + "/summary?q=" + encode(query))).get("totalCustomers").asLong()).isEqualTo(1);
        }
        assertThat(ok(admin.get(API + "?q=" + encode("' OR 1=1 --"))).get("totalItems").asLong()).isZero();
        assertThat(ok(admin.get(API + "/summary?q=" + encode("' OR 1=1 --"))).get("totalCustomers").asLong()).isZero();
    }

    @Test
    void existingOrdersAndProfileChangesAppearWithoutRebuildingReports() throws Exception {
        long alice = customer("alice"); long order = seed(alice, "pending", "unpaid", "18.50");
        Browser admin = admin();
        assertThat(ok(admin.get(API + "/" + alice)).get("orderCount").asLong()).isEqualTo(1);
        jdbc.update("UPDATE users SET username = 'Updated name', email = 'new@example.com', phone = '12345' WHERE user_id = ?", alice);
        jdbc.update("UPDATE orders SET status = 'completed', payment_status = 'paid' WHERE order_id = ?", order);
        JsonNode updated = ok(admin.get(API + "/" + alice));
        assertThat(updated.get("username").asText()).isEqualTo("Updated name");
        assertThat(updated.get("email").asText()).isEqualTo("new@example.com");
        assertThat(updated.get("phone").asText()).isEqualTo("12345");
        assertThat(updated.get("completedOrderCount").asLong()).isEqualTo(1);
        assertThat(updated.get("totalSpent").decimalValue()).isEqualByComparingTo("18.50");
        assertThat(ok(admin.get(API + "?q=new%40example.com")).get("totalItems").asLong()).isEqualTo(1);
        jdbc.update("UPDATE orders SET payment_status = 'refunded' WHERE order_id = ?", order);
        assertThat(ok(admin.get(API + "/" + alice)).get("totalSpent").decimalValue()).isEqualByComparingTo("0");
        jdbc.update("DELETE FROM products WHERE product_id = 1");
        assertThat(ok(admin.get(API + "/" + alice)).get("orderCount").asLong()).isEqualTo(1);
        assertThat(ok(admin.get(API + "/" + alice)).get("orderValue").decimalValue()).isEqualByComparingTo("18.50");
    }

    @Test
    void checkoutAndAdminCancellationUpdateReportsWithoutChangingPaymentState() throws Exception {
        Browser admin = admin(), customer = new Browser();
        created(customer.send("POST", "/api/auth/register", Map.of("username", "Alice", "email", "alice@example.com", "password", "correct-password")));
        ok(customer.send("POST", "/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        created(customer.send("POST", "/api/account/addresses/add", Map.of("fullName", "Alice", "addressLine", "Street")));
        ok(customer.send("POST", "/api/cart/add", Map.of("productId", 1, "quantity", 2, "selectedFormat", "Paperback")));
        JsonNode order = created(customer.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER")));
        long id = userId("alice@example.com");
        JsonNode report = ok(admin.get(API + "/" + id));
        assertThat(report.get("orderCount").asLong()).isEqualTo(1);
        assertThat(report.get("pendingOrderCount").asLong()).isEqualTo(1);
        assertThat(report.get("orderValue")).isEqualTo(order.get("totalPrice"));
        assertThat(report.get("totalSpent").decimalValue()).isEqualByComparingTo("0");
        ok(admin.send("PUT", "/api/admin/orders/" + order.get("orderId").asLong() + "/status",
            Map.of("status", "cancelled", "expectedVersion", 0, "reason", "Cancelled")));
        JsonNode cancelled = ok(admin.get(API + "/" + id));
        assertThat(cancelled.get("orderCount").asLong()).isEqualTo(1);
        assertThat(cancelled.get("cancelledOrderCount").asLong()).isEqualTo(1);
        assertThat(cancelled.get("pendingOrderCount").asLong()).isZero();
        assertThat(cancelled.get("orderValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(ok(admin.get("/api/admin/orders?customerId=" + id)).at("/items/0/order/status").asText()).isEqualTo("cancelled");
    }

    @Test
    void deletingCustomersRemovesTheirReportAndTotals() throws Exception {
        Browser admin = admin(); long id = customer("alice"); seed(id, "completed", "paid", "10.00");
        assertThat(ok(admin.get(API + "/summary")).get("totalOrders").asLong()).isEqualTo(1);
        jdbc.update("DELETE FROM users WHERE user_id = ?", id);
        assertError(admin.get(API + "/" + id), 404, "ADMIN_CUSTOMER_NOT_FOUND");
        assertThat(ok(admin.get(API + "/summary")).get("totalCustomers").asLong()).isZero();
        assertThat(ok(admin.get(API + "/summary")).get("totalSpent").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void invalidIdsPagesAndFiltersReturnDomainErrors() throws Exception {
        Browser admin = admin();
        for (String id : List.of("0", "-1", "1.5", "abc", "9223372036854775808"))
            assertError(admin.get(API + "/" + id), 400, "INVALID_ADMIN_CUSTOMER_REQUEST");
        assertError(admin.get(API + "/999999"), 404, "ADMIN_CUSTOMER_NOT_FOUND");
        for (String page : List.of("0", "-1", "abc", "1.5", "2147483648"))
            assertError(admin.get(API + "?page=" + page), 400, "INVALID_ADMIN_CUSTOMER_REQUEST");
        for (String activity : List.of("unknown", "active", "")) {
            assertError(admin.get(API + "?activity=" + activity), 400, "INVALID_ADMIN_CUSTOMER_REQUEST");
            assertError(admin.get(API + "/summary?activity=" + activity), 400, "INVALID_ADMIN_CUSTOMER_REQUEST");
        }
        for (String sort : List.of("unknown", "username", ""))
            assertError(admin.get(API + "?sort=" + sort), 400, "INVALID_ADMIN_CUSTOMER_REQUEST");
        assertError(admin.get(API + "?q=" + "x".repeat(256)), 400, "INVALID_ADMIN_CUSTOMER_REQUEST");
        assertError(admin.get(API + "/summary?q=" + "x".repeat(256)), 400, "INVALID_ADMIN_CUSTOMER_REQUEST");
    }

    @Test
    void guestsAndCustomersCannotReadAnyReport() throws Exception {
        Browser admin = admin(), guest = new Browser(), customer = new Browser();
        created(customer.send("POST", "/api/auth/register", Map.of("username", "Alice", "email", "alice@example.com", "password", "correct-password")));
        ok(customer.send("POST", "/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        for (String path : List.of(API, API + "/summary", API + "/" + userId("alice@example.com"))) {
            assertThat(guest.get(path).statusCode()).isEqualTo(401);
            assertThat(customer.get(path).statusCode()).isEqualTo(403);
            assertThat(admin.get(path).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void directServiceCallsAreBoundToAdminRoleAndIdentity() throws Exception {
        admin();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@example.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            assertThat(customers.list("admin@example.com", "1", "", null, null).totalItems()).isZero();
            assertThatThrownBy(() -> customers.list("other@example.com", "1", "", null, null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> customers.summary("other@example.com", "", null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> customers.get("other@example.com", "1")).isInstanceOf(AccessDeniedException.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
            assertThatThrownBy(() -> customers.list("alice@example.com", "1", "", null, null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> customers.summary("alice@example.com", "", null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> customers.get("alice@example.com", "1")).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void reportingCannotEditCustomersAndDoesNotMutateOrdersOrStock() throws Exception {
        Browser admin = admin(); long id = customer("alice"); seed(id, "pending", "unpaid", "10.00");
        int stock = jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = 1", Integer.class);
        JsonNode before = ok(admin.get(API + "/" + id));
        ok(admin.get(API)); ok(admin.get(API + "/summary"));
        for (String method : List.of("PUT", "POST", "DELETE"))
            assertThat(admin.send(method, API + "/" + id, Map.of("username", "Changed")).statusCode()).isIn(403, 405);
        assertThat(ok(admin.get(API + "/" + id))).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = 1", Integer.class)).isEqualTo(stock);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE user_id = ?", String.class, id)).isEqualTo("pending");
        assertThat(jdbc.queryForObject("SELECT payment_status FROM orders WHERE user_id = ?", String.class, id)).isEqualTo("unpaid");
    }

    @Test
    void countAndPageUseOneSnapshotWhenACustomerRegistersDuringTheRequest() throws Exception {
        Browser admin = admin(); long first = customer("alice"), second = customer("bob");
        AtomicBoolean inserted = new AtomicBoolean();
        doAnswer(invocation -> {
            Object count = invocation.callRealMethod();
            if (inserted.compareAndSet(false, true)) CompletableFuture.runAsync(() -> customer("carol")).get(10, TimeUnit.SECONDS);
            return count;
        }).when(jdbc).queryForObject(eq(COUNT), eq(Long.class), any(Object[].class));
        try {
            JsonNode page = ok(admin.get(API));
            assertThat(inserted).isTrue();
            assertThat(page.get("totalItems").asLong()).isEqualTo(2);
            assertThat(page.get("items").size()).isEqualTo(2);
            assertThat(page.at("/items/0/userId").asLong()).isEqualTo(second);
            assertThat(page.at("/items/1/userId").asLong()).isEqualTo(first);
        } finally { reset(jdbc); }
        assertThat(ok(admin.get(API)).get("totalItems").asLong()).isEqualTo(3);
    }

    @Test
    void activityAndMetricsUseOneSnapshotWhenAnOrderIsCreatedDuringTheRequest() throws Exception {
        Browser admin = admin(); long id = customer("alice");
        AtomicBoolean inserted = new AtomicBoolean();
        doAnswer(invocation -> {
            Object count = invocation.callRealMethod();
            if (inserted.compareAndSet(false, true)) CompletableFuture.runAsync(() -> seed(id, "completed", "paid", "10.00")).get(10, TimeUnit.SECONDS);
            return count;
        }).when(jdbc).queryForObject(eq(COUNT + " AND coalesce(s.order_count, 0) = 0"), eq(Long.class), any(Object[].class));
        try {
            JsonNode page = ok(admin.get(API + "?activity=no-orders"));
            assertThat(inserted).isTrue();
            assertThat(page.get("totalItems").asLong()).isEqualTo(1);
            assertThat(page.get("items").size()).isEqualTo(1);
            assertThat(page.at("/items/0/orderCount").asLong()).isZero();
            assertThat(page.at("/items/0/totalSpent").decimalValue()).isEqualByComparingTo("0");
        } finally { reset(jdbc); }
        assertThat(ok(admin.get(API + "?activity=no-orders")).get("totalItems").asLong()).isZero();
        assertThat(ok(admin.get(API + "/summary")).get("totalSpent").decimalValue()).isEqualByComparingTo("10.00");
    }

    private long customer(String name) {
        return jdbc.queryForObject("INSERT INTO users (username, email, password_hash) VALUES (?, ?, 'test-fixture-hash') RETURNING user_id",
            Long.class, name, name + "@example.com");
    }

    private long seed(long customer, String status, String payment, String price) {
        long id = jdbc.queryForObject("""
            INSERT INTO orders (user_id, idempotency_key, request_fingerprint, address_snapshot, payment_method,
                total_items, total_quantity, total_price, status, payment_status, created_at)
            VALUES (?, ?, ?, '{"fullName":"Original","addressLine":"Street"}'::jsonb, 'BANK_TRANSFER', 2, 2, ?, ?, ?, '2026-06-01T00:00:00Z')
            RETURNING order_id
            """, Long.class, customer, UUID.randomUUID().toString(), "a".repeat(64), new BigDecimal(price), status, payment);
        for (BigDecimal unitPrice : List.of(new BigDecimal(price), BigDecimal.ZERO))
            jdbc.update("""
                INSERT INTO order_items (order_id, product_id, product_snapshot, selected_attributes, quantity, unit_price)
                VALUES (?, 1, '{"productId":1,"productName":"Original book"}'::jsonb, '{"format":"Paperback"}'::jsonb, 1, ?)
                """, id, unitPrice);
        return id;
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private long userId(String email) { return jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email); }
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
        Browser browser = new Browser();
        ok(browser.send("POST", "/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "correct-password")));
        return browser;
    }
    private class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> send(String method, String path, Object body) throws Exception {
            String token = ok(get("/api/auth/csrf")).get("token").asText();
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", token).method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (path.equals("/api/orders")) request.header("Idempotency-Key", UUID.randomUUID().toString());
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
