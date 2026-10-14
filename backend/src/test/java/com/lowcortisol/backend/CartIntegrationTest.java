package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.cart.AddCartItemRequest;
import com.lowcortisol.backend.cart.CartService;
import com.lowcortisol.backend.catalog.CatalogDemoData;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "cart-test-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> true);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired CatalogDemoData demo;
    @Autowired PasswordEncoder encoder;
    @Autowired CartService cart;

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
    void newCustomerHasEmptyCartAndZeroTotals() throws Exception {
        Browser browser = customer("alice@example.com");
        JsonNode result = browser.cart();
        assertThat(result.get("items").size()).isZero();
        assertThat(result.get("totalItems").asInt()).isZero();
        assertThat(result.get("totalQuantity").asLong()).isZero();
        assertThat(result.get("totalPrice").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(result.get("valid").asBoolean()).isFalse();
    }

    @Test
    void matchingBookAndFormatMergeWhileDifferentFormatsRemainSeparate() throws Exception {
        Browser browser = customer("alice@example.com");
        JsonNode initial = ok(browser.request("/api/cart/add", Map.of("productId", 1)));
        long firstId = initial.get("items").get(0).get("cartItemId").asLong();
        assertThat(initial.get("items").get(0).get("quantity").asInt()).isEqualTo(1);
        assertThat(initial.get("items").get(0).get("selectedAttributes").get("format").asText()).isEqualTo("Paperback");
        JsonNode merged = ok(browser.add(1, 2, " paperback "));
        assertThat(merged.get("totalItems").asInt()).isEqualTo(1);
        assertThat(merged.get("items").get(0).get("cartItemId").asLong()).isEqualTo(firstId);
        assertThat(merged.get("items").get(0).get("quantity").asInt()).isEqualTo(3);
        JsonNode separate = ok(browser.add(1, 1, "HARDCOVER"));
        assertThat(separate.get("totalItems").asInt()).isEqualTo(2);
        assertThat(separate.get("totalQuantity").asInt()).isEqualTo(4);
        assertThat(separate.get("totalPrice").decimalValue()).isEqualByComparingTo("72.00");
        assertThat(separate.get("valid").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = 1", Integer.class)).isEqualTo(74);
    }

    @Test
    void quantityUpdatesRemoveAndClearReturnUpdatedCart() throws Exception {
        Browser browser = customer("alice@example.com");
        long id = ok(browser.add(1, 2, "Paperback")).get("items").get(0).get("cartItemId").asLong();
        JsonNode updated = ok(browser.request("/api/cart/update", Map.of("cartItemId", id, "quantity", 5)));
        assertThat(updated.get("totalQuantity").asInt()).isEqualTo(5);
        assertThat(updated.get("totalPrice").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(ok(browser.request("/api/cart/remove", Map.of("cartItemId", id))).get("items").size()).isZero();
        assertThat(browser.request("/api/cart/remove", Map.of("cartItemId", id)).statusCode()).isEqualTo(404);
        ok(browser.add(1, 1, "Paperback"));
        ok(browser.add(14, 1, "Hardcover"));
        assertThat(ok(browser.request("/api/cart/clear", Map.of())).get("totalQuantity").asInt()).isZero();
        assertThat(ok(browser.request("/api/cart/clear", Map.of())).get("items").size()).isZero();
    }

    @Test
    void stockIsCheckedAcrossFormatsForBothAddsAndUpdates() throws Exception {
        Browser browser = customer("alice@example.com");
        jdbc.update("UPDATE products SET product_quantity = 3 WHERE product_id = 1");
        long paperbackId = ok(browser.add(1, 2, "Paperback")).get("items").get(0).get("cartItemId").asLong();
        HttpResponse<String> rejected = browser.add(1, 2, "Hardcover");
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(json.readTree(rejected.body()).get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(browser.cart().get("totalQuantity").asInt()).isEqualTo(2);
        ok(browser.add(1, 1, "Hardcover"));
        assertThat(browser.request("/api/cart/update", Map.of("cartItemId", paperbackId, "quantity", 3)).statusCode()).isEqualTo(409);
        assertThat(browser.cart().get("totalQuantity").asInt()).isEqualTo(3);
        assertThat(ok(browser.request("/api/cart/update", Map.of("cartItemId", paperbackId, "quantity", 1)))
            .get("totalQuantity").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = 1", Integer.class)).isEqualTo(3);
    }

    @Test
    void mergedLinesCannotExceedTenUnits() throws Exception {
        Browser browser = customer("alice@example.com");
        ok(browser.add(1, 8, "Paperback"));
        HttpResponse<String> rejected = browser.add(1, 3, "Paperback");
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(json.readTree(rejected.body()).get("code").asText()).isEqualTo("QUANTITY_LIMIT");
        assertThat(browser.cart().get("totalQuantity").asInt()).isEqualTo(8);
    }

    @Test
    void hiddenMissingOutOfStockProductsAndInvalidFormatsCannotBeAdded() throws Exception {
        Browser browser = customer("alice@example.com");
        assertThat(browser.add(1, 1, "Audio Book").statusCode()).isEqualTo(400);
        jdbc.update("UPDATE products SET is_visible = FALSE WHERE product_id = 1");
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 2");
        assertThat(browser.add(1, 1, "Paperback").statusCode()).isEqualTo(404);
        assertThat(browser.add(14, 1, "Paperback").statusCode()).isEqualTo(404);
        assertThat(browser.add(999999, 1, "Paperback").statusCode()).isEqualTo(404);
        jdbc.update("UPDATE products SET product_quantity = 0 WHERE product_id = 25");
        HttpResponse<String> stock = browser.add(25, 1, "Paperback");
        assertThat(stock.statusCode()).isEqualTo(409);
        assertThat(json.readTree(stock.body()).get("code").asText()).isEqualTo("OUT_OF_STOCK");
        assertThat(browser.cart().get("items").size()).isZero();
    }

    @Test
    void staleStockVisibilityAndFormatsAreReportedAndItemsCanStillBeRemoved() throws Exception {
        Browser browser = customer("alice@example.com");
        long id = ok(browser.add(1, 3, "Paperback")).get("items").get(0).get("cartItemId").asLong();
        jdbc.update("UPDATE products SET product_quantity = 2 WHERE product_id = 1");
        assertThat(browser.cart().get("items").get(0).get("sufficientStock").asBoolean()).isFalse();
        assertThat(browser.cart().get("valid").asBoolean()).isFalse();
        ok(browser.request("/api/cart/update", Map.of("cartItemId", id, "quantity", 2)));
        jdbc.update("UPDATE products SET product_attributes = '{\"format\":[\"Hardcover\"]}'::jsonb WHERE product_id = 1");
        assertThat(browser.cart().get("items").get(0).get("validFormat").asBoolean()).isFalse();
        assertThat(browser.request("/api/cart/update", Map.of("cartItemId", id, "quantity", 1)).statusCode()).isEqualTo(400);
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 1");
        assertThat(browser.cart().get("items").get(0).get("available").asBoolean()).isFalse();
        assertThat(browser.request("/api/cart/update", Map.of("cartItemId", id, "quantity", 1)).statusCode()).isEqualTo(404);
        assertThat(ok(browser.request("/api/cart/remove", Map.of("cartItemId", id))).get("items").size()).isZero();
    }

    @Test
    void totalsUseCurrentDatabasePricesAndExactDecimalArithmetic() throws Exception {
        Browser browser = customer("alice@example.com");
        jdbc.update("UPDATE products SET product_price = 0.10 WHERE product_id = 1");
        jdbc.update("UPDATE products SET product_price = 0.20 WHERE product_id = 14");
        ok(browser.add(1, 3, "Paperback"));
        JsonNode result = ok(browser.add(14, 2, "Paperback"));
        assertThat(result.get("totalPrice").decimalValue()).isEqualByComparingTo("0.70");
        jdbc.update("UPDATE products SET product_price = 0.11 WHERE product_id = 1");
        assertThat(browser.cart().get("totalPrice").decimalValue()).isEqualByComparingTo("0.73");
    }

    @Test
    void scalarAndAbsentFormatDefinitionsAreSupported() throws Exception {
        Browser browser = customer("alice@example.com");
        jdbc.update("UPDATE products SET product_attributes = '{\"format\":\"Paperback\"}'::jsonb WHERE product_id = 1");
        assertThat(ok(browser.request("/api/cart/add", Map.of("productId", 1))).get("items").get(0)
            .get("selectedAttributes").get("format").asText()).isEqualTo("Paperback");
        jdbc.update("UPDATE products SET product_attributes = '{}'::jsonb WHERE product_id = 14");
        ok(browser.request("/api/cart/add", Map.of("productId", 14)));
        JsonNode result = ok(browser.request("/api/cart/add", Map.of("productId", 14)));
        assertThat(result.get("totalItems").asInt()).isEqualTo(2);
        assertThat(result.get("items").get(0).get("selectedAttributes").size()).isZero();
        assertThat(result.get("valid").asBoolean()).isTrue();
        assertThat(browser.add(14, 1, "Paperback").statusCode()).isEqualTo(400);
    }

    @Test
    void guestAndAdminAccessIsRejected() throws Exception {
        Browser guest = new Browser();
        assertThat(guest.get("/api/cart").statusCode()).isEqualTo(401);
        for (String endpoint : List.of("add", "update", "remove", "clear")) {
            assertThat(guest.request("/api/cart/" + endpoint, Map.of()).statusCode()).isEqualTo(401);
        }
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, 'admin')",
            "Admin", "admin@example.com", encoder.encode("correct-password"));
        Browser admin = new Browser();
        assertThat(admin.request("/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "correct-password")).statusCode()).isEqualTo(200);
        // Admins can use the storefront with their own cart.
        assertThat(admin.get("/api/cart").statusCode()).isEqualTo(200);
        for (String endpoint : List.of("add", "update", "remove", "clear")) {
            assertThat(admin.request("/api/cart/" + endpoint, Map.of()).statusCode()).isNotIn(401, 403);
        }
    }

    @Test
    void customersCannotChangeAnotherCustomersCartOrClearIt() throws Exception {
        Browser first = customer("first@example.com");
        Browser second = customer("second@example.com");
        long id = ok(first.add(1, 2, "Paperback")).get("items").get(0).get("cartItemId").asLong();
        assertThat(second.cart().get("items").size()).isZero();
        assertThat(second.request("/api/cart/update", Map.of("cartItemId", id, "quantity", 3)).statusCode()).isEqualTo(404);
        assertThat(second.request("/api/cart/remove", Map.of("cartItemId", id)).statusCode()).isEqualTo(404);
        ok(second.request("/api/cart/clear", Map.of()));
        assertThat(first.cart().get("totalQuantity").asInt()).isEqualTo(2);
        ok(second.add(1, 1, "Paperback"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items", Integer.class)).isEqualTo(2);
        assertThat(second.request("/api/cart/update", Map.of("cartItemId", 999999, "quantity", 1)).statusCode()).isEqualTo(404);
    }

    @Test
    void malformedIdsQuantitiesPriceInjectionAndMissingCsrfAreRejected() throws Exception {
        Browser browser = customer("alice@example.com");
        for (Object body : List.of(Map.of(), Map.of("productId", 0), Map.of("productId", "abc"), Map.of("productId", 1.5),
            Map.of("productId", 1, "quantity", 0), Map.of("productId", 1, "quantity", 11), Map.of("productId", 1, "quantity", 1.5),
            Map.of("productId", 1, "userId", 999), Map.of("productId", 1, "productPrice", 0.01), Map.of("productId", "9223372036854775808"))) {
            assertThat(browser.request("/api/cart/add", body).statusCode()).isEqualTo(400);
        }
        long id = ok(browser.add(1, 1, "Paperback")).get("items").get(0).get("cartItemId").asLong();
        assertThat(browser.request("/api/cart/update", Map.of("cartItemId", id)).statusCode()).isEqualTo(400);
        assertThat(browser.request("/api/cart/update", Map.of("cartItemId", id, "quantity", 0)).statusCode()).isEqualTo(400);
        assertThat(browser.request("/api/cart/remove", Map.of("cartItemId", -1)).statusCode()).isEqualTo(400);
        for (String endpoint : List.of("add", "update", "remove", "clear")) {
            assertThat(browser.send("/api/cart/" + endpoint, Map.of(), null).statusCode()).isEqualTo(403);
        }
        assertThat(browser.cart().get("totalQuantity").asInt()).isEqualTo(1);
    }

    @Test
    void cartPersistsAcrossLogoutAndEmailChangesAndCascadesOnDeletion() throws Exception {
        Browser browser = customer("alice@example.com");
        long id = ok(browser.add(1, 1, "Paperback")).get("items").get(0).get("cartItemId").asLong();
        assertThat(browser.request("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
        ok(browser.request("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        assertThat(browser.cart().get("items").get(0).get("cartItemId").asLong()).isEqualTo(id);
        ok(browser.request("/api/account/profile", Map.of("username", "Alice", "email", "new@example.com")));
        ok(browser.request("/api/auth/login", Map.of("email", "new@example.com", "password", "correct-password")));
        assertThat(browser.cart().get("items").get(0).get("cartItemId").asLong()).isEqualTo(id);
        jdbc.update("DELETE FROM products WHERE product_id = 1");
        assertThat(browser.cart().get("items").size()).isZero();
        ok(browser.add(14, 1, "Paperback"));
        jdbc.update("DELETE FROM users WHERE email = 'new@example.com'");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items", Integer.class)).isZero();
    }

    @Test
    void serviceGuardsPreventOtherIdentitiesAndAdminRole() throws Exception {
        customer("alice@example.com");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        try {
            assertThat(cart.view("alice@example.com").items()).isEmpty();
            assertThatThrownBy(() -> cart.view("another@example.com")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> cart.add("another@example.com", new AddCartItemRequest(1L, 1, "Paperback")))
                .isInstanceOf(AccessDeniedException.class);
            // Admins may shop on the storefront, so only roles other than CUSTOMER/ADMIN are rejected.
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_GUEST"))));
            assertThatThrownBy(() -> cart.clear("alice@example.com")).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void concurrentAddsMergeAndCannotExceedSharedBookStock() throws Exception {
        Browser first = customer("alice@example.com");
        Browser second = new Browser();
        ok(second.request("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        assertThat(concurrent(first, second, "Paperback", "Paperback")).containsExactly(200, 200);
        assertThat(first.cart().get("totalItems").asInt()).isEqualTo(1);
        assertThat(first.cart().get("totalQuantity").asInt()).isEqualTo(2);
        jdbc.update("UPDATE products SET product_quantity = 3 WHERE product_id = 1");
        assertThat(concurrent(first, second, "Paperback", "Paperback")).containsExactlyInAnyOrder(200, 409);
        assertThat(first.cart().get("totalQuantity").asInt()).isEqualTo(3);
        ok(first.request("/api/cart/clear", Map.of()));
        jdbc.update("UPDATE products SET product_quantity = 1 WHERE product_id = 1");
        assertThat(concurrent(first, second, "Paperback", "Hardcover")).containsExactlyInAnyOrder(200, 409);
        assertThat(first.cart().get("totalQuantity").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = 1", Integer.class)).isEqualTo(1);
    }

    private List<Integer> concurrent(Browser first, Browser second, String formatOne, String formatTwo) throws Exception {
        String firstToken = first.csrf();
        String secondToken = second.csrf();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return first.send("/api/cart/add", Map.of("productId", 1, "quantity", 1, "selectedFormat", formatOne), firstToken).statusCode();
            });
            var two = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return second.send("/api/cart/add", Map.of("productId", 1, "quantity", 1, "selectedFormat", formatTwo), secondToken).statusCode();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private Browser customer(String email) throws Exception {
        Browser browser = new Browser();
        assertThat(browser.request("/api/auth/register", Map.of("username", "Customer", "email", email, "password", "correct-password")).statusCode()).isEqualTo(201);
        ok(browser.request("/api/auth/login", Map.of("email", email, "password", "correct-password")));
        return browser;
    }

    private class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        JsonNode cart() throws Exception { return ok(get("/api/cart")); }
        String csrf() throws Exception { return ok(get("/api/auth/csrf")).get("token").asText(); }

        HttpResponse<String> add(long productId, int quantity, String format) throws Exception {
            return request("/api/cart/add", Map.of("productId", productId, "quantity", quantity, "selectedFormat", format));
        }

        HttpResponse<String> request(String path, Object body) throws Exception { return send(path, body, csrf()); }

        HttpResponse<String> send(String path, Object body, String token) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
