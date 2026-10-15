package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.catalog.CatalogDemoData;
import com.lowcortisol.backend.wishlist.WishlistService;
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
class WishlistIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "wishlist-test-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> true);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired CatalogDemoData demo;
    @Autowired PasswordEncoder encoder;
    @Autowired WishlistService wishlist;

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
    void newCustomerHasEmptyWishlistAndUnsavedProductStatus() throws Exception {
        Browser customer = customer("alice@example.com");
        JsonNode list = customer.ok("/api/account/wishlist");
        assertThat(list.get("items").size()).isZero();
        assertThat(list.get("totalItems").asInt()).isZero();
        assertThat(customer.ok("/api/account/wishlist/1").get("wishlisted").asBoolean()).isFalse();
        assertThat(customer.ok("/api/account/wishlist/999999").get("wishlisted").asBoolean()).isFalse();
    }

    @Test
    void repeatedAddsKeepTheOriginalWishlistEntry() throws Exception {
        Browser customer = customer("alice@example.com");
        assertThat(action(customer.post("add", 1))).isEqualTo("added");
        long id = customer.ok("/api/account/wishlist").get("items").get(0).get("wishlistId").asLong();
        assertThat(action(customer.post("add", 1))).isEqualTo("unchanged");
        assertThat(customer.ok("/api/account/wishlist").get("items").get(0).get("wishlistId").asLong()).isEqualTo(id);
        assertThat(customer.ok("/api/account/wishlist/1").get("wishlisted").asBoolean()).isTrue();
    }

    @Test
    void wishlistReturnsNewestProductsWithDetailsAndAvailability() throws Exception {
        Browser customer = customer("alice@example.com");
        assertThat(action(customer.post("add", 1))).isEqualTo("added");
        assertThat(action(customer.post("add", 14))).isEqualTo("added");
        JsonNode list = customer.ok("/api/account/wishlist");
        assertThat(list.get("totalItems").asInt()).isEqualTo(2);
        JsonNode newest = list.get("items").get(0);
        assertThat(newest.get("product").get("productId").asInt()).isEqualTo(14);
        assertThat(newest.get("product").get("productAttributes").get("author").asText()).isEqualTo("J.K. Rowling");
        assertThat(newest.get("available").asBoolean()).isTrue();
        assertThat(newest.get("savedAt").asText()).isNotBlank();
    }

    @Test
    void repeatedRemovalsLeaveOtherWishlistEntriesIntact() throws Exception {
        Browser customer = customer("alice@example.com");
        assertThat(action(customer.post("add", 1))).isEqualTo("added");
        assertThat(action(customer.post("add", 14))).isEqualTo("added");
        assertThat(action(customer.post("remove", 1))).isEqualTo("removed");
        assertThat(action(customer.post("remove", 1))).isEqualTo("unchanged");
        assertThat(customer.ok("/api/account/wishlist/1").get("wishlisted").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wishlist", Integer.class)).isEqualTo(1);
    }

    @Test
    void toggleAddsAndRemovesAndZeroStockBooksCanBeSaved() throws Exception {
        Browser customer = customer("alice@example.com");
        jdbc.update("UPDATE products SET product_quantity = 0 WHERE product_id = 1");
        HttpResponse<String> added = customer.post("toggle", 1);
        assertThat(action(added)).isEqualTo("added");
        assertThat(json.readTree(added.body()).get("wishlisted").asBoolean()).isTrue();
        JsonNode item = customer.ok("/api/account/wishlist").get("items").get(0);
        assertThat(item.get("available").asBoolean()).isTrue();
        assertThat(item.get("product").get("inStock").asBoolean()).isFalse();
        HttpResponse<String> removed = customer.post("toggle", 1);
        assertThat(action(removed)).isEqualTo("removed");
        assertThat(json.readTree(removed.body()).get("wishlisted").asBoolean()).isFalse();
        assertThat(customer.ok("/api/account/wishlist").get("items").size()).isZero();
    }

    @Test
    void guestsAndAdminsCannotAccessCustomerWishlists() throws Exception {
        Browser guest = new Browser();
        assertThat(guest.get("/api/account/wishlist").statusCode()).isEqualTo(401);
        assertThat(guest.get("/api/account/wishlist/1").statusCode()).isEqualTo(401);
        for (String endpoint : List.of("add", "remove", "toggle")) {
            assertThat(guest.post(endpoint, 1).statusCode()).isEqualTo(401);
        }
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, 'admin')",
            "Admin", "admin@example.com", encoder.encode("correct-password"));
        Browser admin = new Browser();
        assertThat(admin.request("/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "correct-password")).statusCode())
            .isEqualTo(200);
        // Admins can use the storefront with their own wishlist; customers' wishlists stay untouched.
        assertThat(admin.get("/api/account/wishlist").statusCode()).isEqualTo(200);
        assertThat(admin.get("/api/account/wishlist/1").statusCode()).isEqualTo(200);
        for (String endpoint : List.of("add", "remove", "toggle")) {
            assertThat(admin.post(endpoint, 1).statusCode()).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wishlist w JOIN users u ON u.user_id = w.user_id WHERE u.role = 'customer'", Integer.class)).isZero();
    }

    @Test
    void customersCannotReadOrRemoveEachOthersEntries() throws Exception {
        Browser first = customer("first@example.com");
        Browser second = customer("second@example.com");
        assertThat(action(first.post("add", 1))).isEqualTo("added");
        assertThat(second.ok("/api/account/wishlist/1").get("wishlisted").asBoolean()).isFalse();
        assertThat(action(second.post("remove", 1))).isEqualTo("unchanged");
        assertThat(first.ok("/api/account/wishlist/1").get("wishlisted").asBoolean()).isTrue();
        assertThat(action(second.post("add", 1))).isEqualTo("added");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wishlist", Integer.class)).isEqualTo(2);
        assertThat(action(first.post("remove", 1))).isEqualTo("removed");
        assertThat(second.ok("/api/account/wishlist/1").get("wishlisted").asBoolean()).isTrue();
        assertThat(first.ok("/api/account/wishlist").get("items").size()).isZero();
    }

    @Test
    void invalidInputsOwnershipInjectionAndMissingCsrfDoNotWriteRows() throws Exception {
        Browser customer = customer("alice@example.com");
        for (Object body : List.of(Map.of(), Map.of("productId", 0), Map.of("productId", -1), Map.of("productId", "abc"),
            Map.of("productId", 1.5), Map.of("productId", "9223372036854775808"), Map.of("productId", 1, "userId", 999),
            Map.of("productId", 1, "role", "admin"))) {
            assertThat(customer.request("/api/account/wishlist/add", body).statusCode()).isEqualTo(400);
        }
        for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
            assertThat(customer.get("/api/account/wishlist/" + id).statusCode()).isEqualTo(400);
        }
        for (String endpoint : List.of("add", "remove", "toggle")) {
            assertThat(customer.send("/api/account/wishlist/" + endpoint, Map.of("productId", 1), null).statusCode()).isEqualTo(403);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wishlist", Integer.class)).isZero();
    }

    @Test
    void newSavesRejectMissingHiddenProductsAndHiddenCategories() throws Exception {
        Browser customer = customer("alice@example.com");
        jdbc.update("UPDATE products SET is_visible = FALSE WHERE product_id = 1");
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 2");
        for (int id : List.of(1, 14, 999999)) {
            assertThat(customer.post("add", id).statusCode()).isEqualTo(404);
            assertThat(customer.post("toggle", id).statusCode()).isEqualTo(404);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wishlist", Integer.class)).isZero();
    }

    @Test
    void previouslySavedHiddenBooksRemainListedAndCanBeRemoved() throws Exception {
        Browser customer = customer("alice@example.com");
        assertThat(action(customer.post("add", 1))).isEqualTo("added");
        assertThat(action(customer.post("add", 14))).isEqualTo("added");
        jdbc.update("UPDATE products SET is_visible = FALSE WHERE product_id = 1");
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 2");
        JsonNode list = customer.ok("/api/account/wishlist");
        assertThat(list.get("totalItems").asInt()).isEqualTo(2);
        list.get("items").forEach(item -> assertThat(item.get("available").asBoolean()).isFalse());
        assertThat(customer.ok("/api/account/wishlist/1").get("wishlisted").asBoolean()).isTrue();
        assertThat(action(customer.post("toggle", 1))).isEqualTo("removed");
        assertThat(action(customer.post("remove", 14))).isEqualTo("removed");
        assertThat(customer.post("toggle", 1).statusCode()).isEqualTo(404);
    }

    @Test
    void wishlistPersistsAcrossLogoutAndEmailChanges() throws Exception {
        Browser customer = customer("alice@example.com");
        assertThat(action(customer.post("add", 1))).isEqualTo("added");
        long savedId = customer.ok("/api/account/wishlist").get("items").get(0).get("wishlistId").asLong();
        assertThat(customer.request("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
        assertThat(customer.request("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")).statusCode()).isEqualTo(200);
        assertThat(customer.ok("/api/account/wishlist/1").get("wishlisted").asBoolean()).isTrue();
        assertThat(customer.request("/api/account/profile", Map.of("username", "Alice", "email", "new@example.com")).statusCode()).isEqualTo(200);
        assertThat(customer.request("/api/auth/login", Map.of("email", "new@example.com", "password", "correct-password")).statusCode()).isEqualTo(200);
        assertThat(customer.ok("/api/account/wishlist").get("items").get(0).get("wishlistId").asLong()).isEqualTo(savedId);
    }

    @Test
    void deletingUserOrProductCascadesWishlistCleanup() throws Exception {
        Browser customer = customer("alice@example.com");
        assertThat(action(customer.post("add", 1))).isEqualTo("added");
        assertThat(action(customer.post("add", 14))).isEqualTo("added");
        jdbc.update("DELETE FROM products WHERE product_id = 1");
        assertThat(customer.ok("/api/account/wishlist/1").get("wishlisted").asBoolean()).isFalse();
        assertThat(customer.ok("/api/account/wishlist").get("totalItems").asInt()).isEqualTo(1);
        jdbc.update("DELETE FROM users WHERE email = 'alice@example.com'");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wishlist", Integer.class)).isZero();
    }

    @Test
    void serviceAuthorizationRejectsOtherIdentitiesAndAdminRole() throws Exception {
        customer("alice@example.com");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        try {
            assertThat(wishlist.list("alice@example.com").totalItems()).isZero();
            assertThatThrownBy(() -> wishlist.list("another@example.com")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> wishlist.add("another@example.com", 1)).isInstanceOf(AccessDeniedException.class);
            // Admins may shop on the storefront, so only roles other than CUSTOMER/ADMIN are rejected.
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_GUEST"))));
            assertThatThrownBy(() -> wishlist.list("alice@example.com")).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void concurrentAddsDoNotDuplicateAndConcurrentTogglesDoNotLoseChanges() throws Exception {
        Browser first = customer("alice@example.com");
        Browser second = new Browser();
        assertThat(second.request("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")).statusCode()).isEqualTo(200);
        List<String> adds = concurrent(first, second, "add");
        assertThat(adds).containsExactlyInAnyOrder("added", "unchanged");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wishlist", Integer.class)).isEqualTo(1);
        assertThat(action(first.post("remove", 1))).isEqualTo("removed");
        assertThat(concurrent(first, second, "toggle")).containsExactlyInAnyOrder("added", "removed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wishlist", Integer.class)).isZero();
    }

    private List<String> concurrent(Browser first, Browser second, String endpoint) throws Exception {
        String firstToken = first.csrf();
        String secondToken = second.csrf();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return action(first.send("/api/account/wishlist/" + endpoint, Map.of("productId", 1), firstToken));
            });
            var two = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return action(second.send("/api/account/wishlist/" + endpoint, Map.of("productId", 1), secondToken));
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private String action(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("success").asBoolean()).isTrue();
        return json.readTree(response.body()).get("action").asText();
    }

    private Browser customer(String email) throws Exception {
        Browser browser = new Browser();
        assertThat(browser.request("/api/auth/register", Map.of("username", "Customer", "email", email, "password", "correct-password")).statusCode()).isEqualTo(201);
        assertThat(browser.request("/api/auth/login", Map.of("email", email, "password", "correct-password")).statusCode()).isEqualTo(200);
        return browser;
    }

    private class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        JsonNode ok(String path) throws Exception {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).isEqualTo(200);
            return json.readTree(response.body());
        }

        String csrf() throws Exception {
            return ok("/api/auth/csrf").get("token").asText();
        }

        HttpResponse<String> post(String endpoint, long productId) throws Exception {
            return request("/api/account/wishlist/" + endpoint, Map.of("productId", productId));
        }

        HttpResponse<String> request(String path, Object body) throws Exception {
            return send(path, body, csrf());
        }

        HttpResponse<String> send(String path, Object body, String token) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
