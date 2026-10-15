package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.admindashboard.AdminDashboardService;
import com.lowcortisol.backend.catalog.CatalogDemoData;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminDashboardIntegrationTest {
    private static final String API = "/api/admin/dashboard";
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "admin-dashboard-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> true);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @MockitoSpyBean JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired CatalogDemoData demo;
    @Autowired AdminDashboardService dashboard;
    @Autowired PlatformTransactionManager transactions;

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
    void emptyStoreReturnsZerosAndThirtyConsecutiveEmptyDays() throws Exception {
        Browser admin = admin(); jdbc.update("DELETE FROM products"); jdbc.update("DELETE FROM categories");
        JsonNode report = ok(admin.get(API));
        for (String key : List.of("totalProducts", "totalCustomers", "totalOrders", "totalRevenue", "orderValue",
            "pendingOrders", "completedOrders", "cancelledOrders", "customersWithOrders", "customersWithoutOrders"))
            assertThat(report.get(key).decimalValue()).isEqualByComparingTo("0");
        assertThat(report.get("currency").asText()).isEqualTo("USD");
        assertThat(report.get("recentOrders").isEmpty()).isTrue();
        assertThat(report.at("/inventory/totalUnits").asLong()).isZero();
        assertThat(report.at("/inventory/lowStockThreshold").asInt()).isEqualTo(5);
        assertThat(report.at("/salesTrend/timeZone").asText()).isEqualTo("UTC");
        assertThat(report.at("/salesTrend/days").asInt()).isEqualTo(30);
        LocalDate end = OffsetDateTime.parse(report.get("generatedAt").asText()).toLocalDate();
        assertThat(report.at("/salesTrend/startDate").asText()).isEqualTo(end.minusDays(29).toString());
        assertThat(report.at("/salesTrend/endDate").asText()).isEqualTo(end.toString());
        assertThat(report.at("/salesTrend/items").size()).isEqualTo(30);
        int index = 0;
        for (JsonNode day : report.at("/salesTrend/items")) {
            assertThat(day.get("date").asText()).isEqualTo(end.minusDays(29).plusDays(index++).toString());
            assertThat(day.get("totalOrders").asLong()).isZero();
            assertThat(day.get("orderValue").decimalValue()).isEqualByComparingTo("0");
            assertThat(day.get("totalRevenue").decimalValue()).isEqualByComparingTo("0");
        }
    }

    @Test
    void lifetimeMetricsMatchCustomerReportingAndExcludeAdminAccountsAndOrders() throws Exception {
        Browser admin = admin(); long alice = customer("alice"); customer("bob");
        String today = today() + "T12:00:00Z";
        seed(alice, "pending", "unpaid", "10.01", today); seed(alice, "completed", "paid", "20.02", today);
        seed(alice, "cancelled", "unpaid", "30.03", today); seed(alice, "cancelled", "paid", "40.04", today);
        seed(alice, "completed", "refunded", "50.05", today); seed(alice, "pending", "paid", "60.06", today);
        seed(alice, "cancelled", "refunded", "70.07", today);
        seed(userId("admin@example.com"), "completed", "paid", "999.99", today);
        JsonNode report = ok(admin.get(API)), customerReport = ok(admin.get("/api/admin/customers/summary"));
        assertThat(report.get("totalCustomers").asLong()).isEqualTo(2);
        assertThat(report.get("customersWithOrders").asLong()).isEqualTo(1);
        assertThat(report.get("customersWithoutOrders").asLong()).isEqualTo(1);
        assertThat(report.get("totalOrders").asLong()).isEqualTo(7);
        assertThat(report.get("pendingOrders").asLong()).isEqualTo(2);
        assertThat(report.get("completedOrders").asLong()).isEqualTo(2);
        assertThat(report.get("cancelledOrders").asLong()).isEqualTo(3);
        assertThat(report.get("orderValue").decimalValue()).isEqualByComparingTo("140.14");
        assertThat(report.get("totalRevenue").decimalValue()).isEqualByComparingTo("120.12");
        assertThat(report.get("totalRevenue")).isEqualTo(customerReport.get("totalSpent"));
        assertThat(report.at("/salesTrend/totalOrders").asLong()).isEqualTo(7);
        assertThat(report.at("/salesTrend/totalRevenue").decimalValue()).isEqualByComparingTo("120.12");
        assertThat(report.at("/salesTrend/orderValue").decimalValue()).isEqualByComparingTo("140.14");
        assertThat(report.get("recentOrders").size()).isEqualTo(7);
    }

    @Test
    void inventoryIncludesHiddenCatalogAndProvidesCorrectStockAlerts() throws Exception {
        Browser admin = admin();
        jdbc.update("DELETE FROM products WHERE product_id NOT IN (1, 2, 3, 4, 14)");
        jdbc.update("UPDATE products SET product_quantity = CASE product_id WHEN 1 THEN 0 WHEN 2 THEN 5 WHEN 3 THEN 6 WHEN 4 THEN 100 ELSE 4 END");
        jdbc.update("UPDATE products SET is_visible = FALSE WHERE product_id = 4");
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 2");
        JsonNode report = ok(admin.get(API));
        assertThat(report.get("totalProducts").asLong()).isEqualTo(5);
        assertThat(report.get("inventory")).isEqualTo(ok(admin.get("/api/admin/inventory/summary")));
        assertThat(report.at("/inventory/totalUnits").asLong()).isEqualTo(115);
        assertThat(report.at("/inventory/outOfStockProducts").asLong()).isEqualTo(1);
        assertThat(report.at("/inventory/lowStockProducts").asLong()).isEqualTo(2);
        assertThat(report.at("/inventory/visibleProducts").asLong()).isEqualTo(3);
        assertThat(report.at("/inventory/hiddenProducts").asLong()).isEqualTo(2);
    }

    @Test
    void recentOrdersAreBoundedAndSortByDateThenIdWithSafeFields() throws Exception {
        Browser admin = admin(); long alice = customer("alice"), bob = customer("bob");
        String date = today() + "T10:00:00Z"; long newest = 0;
        for (int i = 0; i < 13; i++) newest = seed(i % 2 == 0 ? alice : bob, "pending", "unpaid", "1.00", date);
        seed(userId("admin@example.com"), "pending", "paid", "100.00", today().plusDays(1) + "T12:00:00Z");
        jdbc.update("UPDATE users SET username = 'Updated Alice' WHERE user_id = ?", alice);
        JsonNode report = ok(admin.get(API));
        assertThat(report.get("recentOrders").size()).isEqualTo(10);
        assertThat(report.at("/recentOrders/0/orderId").asLong()).isEqualTo(newest);
        assertThat(report.at("/recentOrders/0/username").asText()).isEqualTo("Updated Alice");
        assertThat(report.at("/recentOrders/0/totalItems").asInt()).isEqualTo(2);
        long previous = Long.MAX_VALUE;
        for (JsonNode order : report.get("recentOrders")) {
            assertThat(order.get("orderId").asLong()).isLessThan(previous); previous = order.get("orderId").asLong();
            assertThat(order.get("currency").asText()).isEqualTo("USD");
            assertThat(order.get("paymentStatus").asText()).isEqualTo("unpaid");
            assertThat(order.toString()).doesNotContain("password", "email", "phone", "snapshot", "address", "fingerprint", "idempotency", "savedPaymentMethod");
        }
        long olderId = seed(bob, "completed", "paid", "2.00", today().minusDays(1) + "T23:59:00Z");
        assertThat(ok(admin.get(API)).at("/recentOrders/0/orderId").asLong()).isEqualTo(newest);
        assertThat(olderId).isGreaterThan(newest);
    }

    @Test
    void recentOrdersArePagedTenAtATime() throws Exception {
        Browser admin = admin(); long alice = customer("alice");
        String date = today() + "T10:00:00Z";
        for (int i = 0; i < 13; i++) seed(alice, "pending", "unpaid", "1.00", date);
        JsonNode first = ok(admin.get(API));
        assertThat(first.get("recentOrdersPage").asInt()).isEqualTo(1);
        assertThat(first.get("recentOrdersTotalPages").asLong()).isEqualTo(2);
        JsonNode second = ok(admin.get(API + "?page=2"));
        assertThat(second.get("recentOrders").size()).isEqualTo(3);
        assertThat(second.at("/recentOrders/0/orderId").asLong()).isLessThan(first.at("/recentOrders/9/orderId").asLong());
        assertThat(ok(admin.get(API + "?page=5")).get("recentOrders").isEmpty()).isTrue();
        assertThat(admin.get(API + "?page=0").statusCode()).isEqualTo(400);
    }

    @Test
    void dailyTrendUsesUtcInclusiveDatesAndExcludesOrdersOutsideTheWindow() throws Exception {
        Browser admin = admin(); long alice = customer("alice"); LocalDate today = today();
        seed(alice, "pending", "unpaid", "10.00", today + "T00:00:00Z");
        seed(alice, "completed", "paid", "20.00", today + "T06:59:59+07:00");
        seed(alice, "cancelled", "paid", "30.00", today + "T07:00:00+07:00");
        seed(alice, "completed", "refunded", "40.00", today.minusDays(2) + "T00:00:00Z");
        seed(alice, "pending", "unpaid", "50.00", today.minusDays(3) + "T23:59:59Z");
        seed(alice, "completed", "paid", "60.00", today.plusDays(1) + "T00:00:00Z");
        JsonNode report = ok(admin.get(API + "?days=3")), trend = report.get("salesTrend");
        assertThat(report.get("totalOrders").asLong()).isEqualTo(6);
        assertThat(report.get("totalRevenue").decimalValue()).isEqualByComparingTo("110.00");
        assertThat(trend.get("totalOrders").asLong()).isEqualTo(4);
        assertThat(trend.get("orderValue").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(trend.get("totalRevenue").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(trend.get("items").size()).isEqualTo(3);
        assertThat(trend.at("/items/0/date").asText()).isEqualTo(today.minusDays(2).toString());
        assertThat(trend.at("/items/0/completedOrders").asLong()).isEqualTo(1);
        assertThat(trend.at("/items/0/totalRevenue").decimalValue()).isEqualByComparingTo("0");
        assertThat(trend.at("/items/1/date").asText()).isEqualTo(today.minusDays(1).toString());
        assertThat(trend.at("/items/1/totalRevenue").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(trend.at("/items/2/totalOrders").asLong()).isEqualTo(2);
        assertThat(trend.at("/items/2/pendingOrders").asLong()).isEqualTo(1);
        assertThat(trend.at("/items/2/cancelledOrders").asLong()).isEqualTo(1);
    }

    @Test
    void requestedTrendSizeIsBoundedAndMissingDatesAreFilledWithZero() throws Exception {
        Browser admin = admin(); long alice = customer("alice");
        seed(alice, "completed", "paid", "5.00", today().minusDays(2) + "T12:00:00Z");
        JsonNode one = ok(admin.get(API + "?days=1"));
        assertThat(one.at("/salesTrend/items").size()).isEqualTo(1);
        assertThat(one.at("/salesTrend/totalOrders").asLong()).isZero();
        assertThat(one.get("totalOrders").asLong()).isEqualTo(1);
        JsonNode four = ok(admin.get(API + "?days=4"));
        assertThat(four.at("/salesTrend/items/0/totalOrders").asLong()).isZero();
        assertThat(four.at("/salesTrend/items/1/totalOrders").asLong()).isEqualTo(1);
        assertThat(four.at("/salesTrend/items/2/totalOrders").asLong()).isZero();
        JsonNode maximum = ok(admin.get(API + "?days=366"));
        assertThat(maximum.at("/salesTrend/items").size()).isEqualTo(366);
        assertThat(maximum.at("/salesTrend/startDate").asText()).isEqualTo(today().minusDays(365).toString());
        assertThat(maximum.at("/salesTrend/totalRevenue").decimalValue()).isEqualByComparingTo("5.00");
    }

    @Test
    void moneyUsesExactDecimalsWithoutItemDuplicationOrIntegerOverflow() throws Exception {
        Browser admin = admin(); long alice = customer("alice"); String date = today() + "T12:00:00Z";
        for (int i = 0; i < 3; i++) seed(alice, "completed", "paid", "99999999.99", date);
        seed(alice, "completed", "paid", "0.10", date); seed(alice, "completed", "paid", "0.20", date);
        JsonNode report = ok(admin.get(API));
        assertThat(report.get("totalOrders").asLong()).isEqualTo(5);
        assertThat(report.get("totalRevenue").decimalValue()).isEqualByComparingTo("300000000.27");
        assertThat(report.at("/salesTrend/totalRevenue").decimalValue()).isEqualByComparingTo("300000000.27");
        assertThat(report.get("orderValue").decimalValue()).isEqualByComparingTo("300000000.27");
    }

    @Test
    void checkoutImmediatelyUpdatesDashboardOrderTotalsAndInventory() throws Exception {
        Browser admin = admin(), customer = new Browser();
        created(customer.send("POST", "/api/auth/register", Map.of("username", "Alice", "email", "alice@example.com", "password", "correct-password")));
        ok(customer.send("POST", "/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        created(customer.send("POST", "/api/account/addresses/add", Map.of("fullName", "Alice", "addressLine", "Street")));
        JsonNode before = ok(admin.get(API));
        ok(customer.send("POST", "/api/cart/add", Map.of("productId", 1, "quantity", 2, "selectedFormat", "Paperback")));
        JsonNode order = created(customer.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER")));
        JsonNode placed = ok(admin.get(API));
        assertThat(placed.get("totalOrders").asLong()).isEqualTo(1);
        assertThat(placed.get("pendingOrders").asLong()).isEqualTo(1);
        assertThat(placed.get("totalRevenue").decimalValue()).isEqualByComparingTo("0");
        assertThat(placed.get("orderValue")).isEqualTo(order.get("totalPrice"));
        assertThat(placed.at("/inventory/totalUnits").asLong()).isEqualTo(before.at("/inventory/totalUnits").asLong() - 2);
    }

    @Test
    void cancellationImmediatelyUpdatesDashboardStatusSalesAndRestoresInventory() throws Exception {
        Browser admin = admin(), customer = new Browser();
        created(customer.send("POST", "/api/auth/register", Map.of("username", "Alice", "email", "alice@example.com", "password", "correct-password")));
        ok(customer.send("POST", "/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        created(customer.send("POST", "/api/account/addresses/add", Map.of("fullName", "Alice", "addressLine", "Street")));
        JsonNode before = ok(admin.get(API));
        ok(customer.send("POST", "/api/cart/add", Map.of("productId", 1, "quantity", 2, "selectedFormat", "Paperback")));
        JsonNode order = created(customer.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER")));
        ok(admin.send("PUT", "/api/admin/orders/" + order.get("orderId").asLong() + "/status", Map.of("status", "cancelled", "expectedVersion", 0, "reason", "Cancel")));
        JsonNode cancelled = ok(admin.get(API));
        assertThat(cancelled.get("pendingOrders").asLong()).isZero();
        assertThat(cancelled.get("cancelledOrders").asLong()).isEqualTo(1);
        assertThat(cancelled.get("orderValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(cancelled.at("/salesTrend/orderValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(cancelled.at("/recentOrders/0/status").asText()).isEqualTo("cancelled");
        assertThat(cancelled.at("/inventory/totalUnits")).isEqualTo(before.at("/inventory/totalUnits"));
    }

    @Test
    void refundsAndDeletedSourcesUpdateReportsWithoutLosingHistoricalOrderValues() throws Exception {
        Browser admin = admin(); long alice = customer("alice");
        long id = seed(alice, "completed", "paid", "18.50", today() + "T12:00:00Z");
        assertThat(ok(admin.get(API)).get("totalRevenue").decimalValue()).isEqualByComparingTo("18.50");
        jdbc.update("UPDATE orders SET payment_status = 'refunded' WHERE order_id = ?", id);
        jdbc.update("DELETE FROM products WHERE product_id = 1");
        JsonNode refunded = ok(admin.get(API));
        assertThat(refunded.get("totalProducts").asLong()).isEqualTo(59);
        assertThat(refunded.get("totalOrders").asLong()).isEqualTo(1);
        assertThat(refunded.get("orderValue").decimalValue()).isEqualByComparingTo("18.50");
        assertThat(refunded.get("totalRevenue").decimalValue()).isEqualByComparingTo("0");
        assertThat(refunded.at("/salesTrend/totalRevenue").decimalValue()).isEqualByComparingTo("0");
        jdbc.update("DELETE FROM users WHERE user_id = ?", alice);
        JsonNode removed = ok(admin.get(API));
        assertThat(removed.get("totalOrders").asLong()).isZero();
        assertThat(removed.get("totalCustomers").asLong()).isZero();
        assertThat(removed.get("recentOrders").isEmpty()).isTrue();
        assertThat(removed.at("/salesTrend/totalOrders").asLong()).isZero();
    }

    @Test
    void invalidTrendLengthsReturnDomainErrors() throws Exception {
        Browser admin = admin();
        for (String value : List.of("0", "-1", "abc", "1.5", "367", "2147483648", "", "%2030%20", "1%20OR%201=1")) {
            HttpResponse<String> response = admin.get(API + "?days=" + value);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("INVALID_ADMIN_DASHBOARD_REQUEST");
        }
    }

    @Test
    void endpointAndServiceRequireMatchingAdminIdentity() throws Exception {
        Browser admin = admin(), guest = new Browser(), customer = new Browser();
        created(customer.send("POST", "/api/auth/register", Map.of("username", "Alice", "email", "alice@example.com", "password", "correct-password")));
        ok(customer.send("POST", "/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        assertThat(guest.get(API).statusCode()).isEqualTo(401);
        assertThat(customer.get(API).statusCode()).isEqualTo(403);
        ok(admin.get(API));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@example.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            assertThat(dashboard.get("admin@example.com", "1").totalCustomers()).isEqualTo(1);
            assertThatThrownBy(() -> dashboard.get("other@example.com", "1")).isInstanceOf(AccessDeniedException.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
            assertThatThrownBy(() -> dashboard.get("alice@example.com", "1")).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void readsCannotMutateStoreDataOrProvideWriteOperations() throws Exception {
        Browser admin = admin(); long alice = customer("alice"); seed(alice, "pending", "unpaid", "10.00", today() + "T12:00:00Z");
        JsonNode before = ok(admin.get(API));
        for (String method : List.of("PUT", "POST", "DELETE"))
            assertThat(admin.send(method, API, Map.of("totalRevenue", 999)).statusCode()).isIn(403, 405);
        JsonNode after = ok(admin.get(API));
        for (String key : List.of("inventory", "recentOrders", "salesTrend", "totalProducts", "totalOrders", "totalRevenue"))
            assertThat(after.get(key)).isEqualTo(before.get(key));
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE user_id = ?", String.class, alice)).isEqualTo("pending");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_changes", Long.class)).isZero();
    }

    @Test
    void everySectionUsesOneSnapshotDuringConcurrentStoreChanges() throws Exception {
        Browser admin = admin(); long alice = customer("alice"); seed(alice, "completed", "paid", "10.00", today() + "T12:00:00Z");
        JsonNode before = ok(admin.get(API));
        AtomicBoolean changed = new AtomicBoolean();
        doAnswer(invocation -> {
            Object time = invocation.callRealMethod();
            if (changed.compareAndSet(false, true)) {
                CompletableFuture.runAsync(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    long bob = customer("bob"); seed(bob, "completed", "paid", "20.00", today() + "T12:00:00Z");
                    jdbc.update("UPDATE products SET product_quantity = 3, is_visible = FALSE WHERE product_id = 1");
                })).get(10, TimeUnit.SECONDS);
            }
            return time;
        }).when(jdbc).queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
        try {
            JsonNode during = ok(admin.get(API));
            assertThat(changed).isTrue();
            for (String key : List.of("totalCustomers", "totalOrders", "totalRevenue", "inventory", "recentOrders", "salesTrend"))
                assertThat(during.get(key)).isEqualTo(before.get(key));
        } finally { reset(jdbc); }
        JsonNode after = ok(admin.get(API));
        assertThat(after.get("totalCustomers").asLong()).isEqualTo(2);
        assertThat(after.get("totalOrders").asLong()).isEqualTo(2);
        assertThat(after.get("totalRevenue").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(after.at("/salesTrend/totalRevenue").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(after.at("/inventory/totalUnits").asLong()).isEqualTo(before.at("/inventory/totalUnits").asLong() - 71);
        assertThat(after.get("recentOrders").size()).isEqualTo(2);
    }

    @Test
    void unavailableReportStorageReturnsGenericDatabaseErrorAndRecovers() throws Exception {
        Browser admin = admin();
        jdbc.execute("ALTER VIEW admin_dashboard_daily_sales RENAME TO unavailable_dashboard_sales");
        try {
            HttpResponse<String> response = admin.get(API);
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("DATABASE_ERROR");
            assertThat(response.body()).doesNotContain("SELECT", "admin_dashboard_daily_sales", "SQLException");
        } finally { jdbc.execute("ALTER VIEW unavailable_dashboard_sales RENAME TO admin_dashboard_daily_sales"); }
        ok(admin.get(API));
    }

    private LocalDate today() {
        return jdbc.queryForObject("SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date", LocalDate.class);
    }
    private long customer(String name) {
        return jdbc.queryForObject("INSERT INTO users (username, email, password_hash) VALUES (?, ?, 'test-fixture-hash') RETURNING user_id",
            Long.class, name, name + "@example.com");
    }
    private long seed(long customer, String status, String payment, String price, String created) {
        long id = jdbc.queryForObject("""
            INSERT INTO orders (user_id, idempotency_key, request_fingerprint, address_snapshot, payment_method,
                total_items, total_quantity, total_price, status, payment_status, created_at)
            VALUES (?, ?, ?, '{"fullName":"Original","addressLine":"Street"}'::jsonb, 'BANK_TRANSFER', 2, 2, ?, ?, ?, CAST(? AS timestamptz))
            RETURNING order_id
            """, Long.class, customer, UUID.randomUUID().toString(), "a".repeat(64), new BigDecimal(price), status, payment, created);
        for (BigDecimal unitPrice : List.of(new BigDecimal(price), BigDecimal.ZERO))
            jdbc.update("""
                INSERT INTO order_items (order_id, product_id, product_snapshot, selected_attributes, quantity, unit_price)
                VALUES (?, 1, '{"productId":1,"productName":"Original book"}'::jsonb, '{"format":"Paperback"}'::jsonb, 1, ?)
                """, id, unitPrice);
        return id;
    }
    private long userId(String email) { return jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email); }
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
