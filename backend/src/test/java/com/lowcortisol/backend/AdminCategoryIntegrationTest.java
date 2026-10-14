package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.admincategory.AdminCategoryService;
import com.lowcortisol.backend.admincategory.CategoryWriteRequest;
import com.lowcortisol.backend.admincategory.CategoryVersionRequest;
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
class AdminCategoryIntegrationTest {
    private static final String API = "/api/admin/categories";
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "admin-category-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> true);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired CatalogDemoData demo;
    @Autowired AdminCategoryService categories;

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
    void listRetainsTheExistingArrayContractAndIncludesHiddenCategoriesAndCounts() throws Exception {
        Browser admin = admin();
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 2");
        JsonNode list = ok(admin.get(API));
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isEqualTo(5);
        assertThat(list.get(1).get("isVisible").asBoolean()).isFalse();
        assertThat(list.get(1).get("productCount").asInt()).isEqualTo(12);
        assertThat(list.get(1).get("version").asLong()).isZero();
        assertThat(list.get(1).get("createdAt").asText()).isNotBlank();
        assertThat(list.get(1).get("updatedAt").asText()).isNotBlank();
        assertThat(ok(admin.get(API + "?visibility=visible")).size()).isEqualTo(4);
        assertThat(ok(admin.get(API + "?visibility=%20HIDDEN%20")).get(0).get("categoryId").asLong()).isEqualTo(2);
        JsonNode category = ok(admin.get(API + "/2"));
        assertThat(category).isEqualTo(list.get(1));
    }

    @Test
    void creationNormalizesNameImageAndSlugAndReturnsAnOwnedAdminLocation() throws Exception {
        Browser admin = admin();
        HttpResponse<String> response = admin.send("POST", API, Map.of("categoryName", " Café & Children's Books ", "categoryImage", " cover.png "));
        JsonNode category = created(response);
        assertThat(category.get("categoryId").asLong()).isGreaterThan(5);
        assertThat(category.get("categoryName").asText()).isEqualTo("Café & Children's Books");
        assertThat(category.get("slug").asText()).isEqualTo("cafe-children-s-books");
        assertThat(category.get("categoryImage").asText()).isEqualTo("cover.png");
        assertThat(category.get("isVisible").asBoolean()).isTrue();
        assertThat(category.get("productCount").asLong()).isZero();
        assertThat(category.get("version").asLong()).isZero();
        assertThat(ok(admin.get(response.headers().firstValue("Location").orElseThrow()))).isEqualTo(category);
        assertThat(new Browser().get("/api/categories/" + category.get("slug").asText()).statusCode()).isEqualTo(200);
        assertThat(ok(new Browser().get("/api/categories/" + category.get("slug").asText() + "/products")).get("totalItems").asInt()).isZero();
    }

    @Test
    void unicodeAndNumericNamesGenerateUsablePublicSlugs() throws Exception {
        Browser admin = admin();
        for (String name : List.of("หนังสือ", "2026", "2026/2027")) {
            JsonNode category = created(admin.send("POST", API, Map.of("categoryName", name)));
            assertThat(category.get("slug").asText()).startsWith("category-");
            assertThat(new Browser().get("/api/categories/" + category.get("slug").asText()).statusCode()).isEqualTo(200);
            assertThat(category.get("categoryImage").isNull()).isTrue();
        }
    }

    @Test
    void renamePreservesSlugImageAndVisibilityUnlessExplicitlyReplaced() throws Exception {
        Browser admin = admin();
        long id = created(admin.send("POST", API, Map.of("categoryName", "Original", "slug", " OLD-SLUG ", "categoryImage", "cover.png", "isVisible", false)))
            .get("categoryId").asLong();
        JsonNode renamed = ok(admin.send("PUT", API + "/" + id, Map.of("categoryName", "Renamed", "expectedVersion", 0)));
        assertThat(renamed.get("slug").asText()).isEqualTo("old-slug");
        assertThat(renamed.get("categoryImage").asText()).isEqualTo("cover.png");
        assertThat(renamed.get("isVisible").asBoolean()).isFalse();
        assertThat(renamed.get("version").asLong()).isEqualTo(1);
        JsonNode updated = ok(admin.send("PUT", API + "/" + id,
            Map.of("categoryName", "Renamed", "slug", " NEW-SLUG ", "categoryImage", "", "isVisible", true, "expectedVersion", 1)));
        assertThat(updated.get("categoryImage").isNull()).isTrue();
        assertThat(updated.get("slug").asText()).isEqualTo("new-slug");
        assertThat(new Browser().get("/api/categories/new-slug").statusCode()).isEqualTo(200);
        assertThat(new Browser().get("/api/categories/old-slug").statusCode()).isEqualTo(404);
    }

    @Test
    void slugConflictsRollBackEditsAndNamesCanRepeatWithDifferentSlugs() throws Exception {
        Browser admin = admin();
        long id = created(admin.send("POST", API, Map.of("categoryName", "New category"))).get("categoryId").asLong();
        assertError(admin.send("POST", API, Map.of("categoryName", "New category")), 409, "CATEGORY_SLUG_IN_USE");
        assertError(admin.send("POST", API, Map.of("categoryName", "Other", "slug", " FICTION ")), 409, "CATEGORY_SLUG_IN_USE");
        JsonNode before = ok(admin.get(API + "/" + id));
        assertError(admin.send("PUT", API + "/" + id,
            Map.of("categoryName", "Changed", "slug", "fiction", "isVisible", false, "expectedVersion", 0)), 409, "CATEGORY_SLUG_IN_USE");
        assertThat(ok(admin.get(API + "/" + id))).isEqualTo(before);
        created(admin.send("POST", API, Map.of("categoryName", "New category", "slug", "different-category")));
        assertThat(ok(admin.get(API)).size()).isEqualTo(7);
    }

    @Test
    void categoryEditsUpdateLiveProductsButRetainHistoricalOrderCategoryDetails() throws Exception {
        Browser admin = admin(), customer = customer();
        customer.add();
        JsonNode original = created(customer.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER")));
        ok(admin.send("PUT", API + "/1", Map.of("categoryName", "New fiction name", "slug", "new-fiction", "expectedVersion", 0)));
        JsonNode product = ok(new Browser().get("/api/products/1"));
        assertThat(product.get("categoryName").asText()).isEqualTo("New fiction name");
        assertThat(product.get("categorySlug").asText()).isEqualTo("new-fiction");
        assertThat(new Browser().get("/api/categories/fiction").statusCode()).isEqualTo(404);
        assertThat(ok(customer.get("/api/orders/" + original.get("orderId").asLong()))).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = 1", Integer.class)).isEqualTo(73);
    }

    @Test
    void hiddenCategoriesAffectCatalogCartWishlistAndCheckoutWithoutChangingProductVisibility() throws Exception {
        Browser admin = admin(), customer = customer();
        customer.add();
        ok(customer.send("POST", "/api/account/wishlist/add", Map.of("productId", 1)));
        ok(admin.send("PUT", "/api/admin/products/2/visibility", Map.of("isVisible", false, "expectedVersion", 0)));
        ok(admin.send("PUT", API + "/1/visibility", Map.of("isVisible", false, "expectedVersion", 0)));
        Browser publicBrowser = new Browser();
        assertThat(publicBrowser.get("/api/categories/1").statusCode()).isEqualTo(404);
        assertThat(publicBrowser.get("/api/products/1").statusCode()).isEqualTo(404);
        assertThat(ok(publicBrowser.get("/api/products")).get("totalItems").asInt()).isEqualTo(48);
        assertThat(ok(customer.get("/api/cart")).get("items").get(0).get("available").asBoolean()).isFalse();
        assertThat(ok(customer.get("/api/account/wishlist")).get("items").get(0).get("available").asBoolean()).isFalse();
        assertError(customer.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER")), 409, "PRODUCT_UNAVAILABLE");
        ok(admin.send("PUT", API + "/1/toggle", Map.of("expectedVersion", 1)));
        assertThat(publicBrowser.get("/api/products/1").statusCode()).isEqualTo(200);
        assertThat(publicBrowser.get("/api/products/2").statusCode()).isEqualTo(404);
        assertThat(ok(customer.get("/api/cart")).get("valid").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = 1", Integer.class)).isEqualTo(74);
    }

    @Test
    void toggleRetriesAreRejectedAndExplicitUnchangedVisibilityIsANoOp() throws Exception {
        Browser admin = admin();
        JsonNode before = ok(admin.get(API + "/1"));
        assertThat(ok(admin.send("PUT", API + "/1/visibility", Map.of("isVisible", true, "expectedVersion", 0)))).isEqualTo(before);
        JsonNode toggled = ok(admin.send("PUT", API + "/1/toggle", Map.of("expectedVersion", 0)));
        assertThat(toggled.get("isVisible").asBoolean()).isFalse();
        assertThat(toggled.get("version").asLong()).isEqualTo(1);
        assertError(admin.send("PUT", API + "/1/toggle", Map.of("expectedVersion", 0)), 409, "STALE_CATEGORY_VERSION");
        assertThat(ok(admin.get(API + "/1"))).isEqualTo(toggled);
        assertThat(ok(admin.send("PUT", API + "/1/visibility", Map.of("isVisible", false, "expectedVersion", 1)))).isEqualTo(toggled);
    }

    @Test
    void nonemptyCategoriesCannotBeDeletedAndEmptyCategoriesCan() throws Exception {
        Browser admin = admin();
        assertError(admin.send("DELETE", API + "/1?expectedVersion=0", Map.of()), 409, "CATEGORY_NOT_EMPTY");
        assertThat(ok(admin.get(API + "/1")).get("productCount").asInt()).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(60);
        long id = created(admin.send("POST", API, Map.of("categoryName", "Empty category"))).get("categoryId").asLong();
        assertThat(admin.send("DELETE", API + "/" + id + "?expectedVersion=0", Map.of()).statusCode()).isEqualTo(204);
        assertError(admin.get(API + "/" + id), 404, "ADMIN_CATEGORY_NOT_FOUND");
        assertThat(ok(admin.get(API)).size()).isEqualTo(5);
    }

    @Test
    void searchTreatsWildcardsAndSqlSyntaxAsLiteralText() throws Exception {
        Browser admin = admin();
        long id = created(admin.send("POST", API, Map.of("categoryName", "100%_! Books", "slug", "literal-books"))).get("categoryId").asLong();
        JsonNode list = ok(admin.get(API + "?q=" + URLEncoder.encode("%_!", StandardCharsets.UTF_8)));
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("categoryId").asLong()).isEqualTo(id);
        assertThat(ok(admin.get(API + "?q=literal-books")).size()).isEqualTo(1);
        String literal = "x'); DROP TABLE categories;--";
        created(admin.send("POST", API, Map.of("categoryName", literal, "slug", "sql-text")));
        assertThat(ok(admin.get(API + "?q=" + URLEncoder.encode(literal, StandardCharsets.UTF_8))).size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM categories", Integer.class)).isEqualTo(7);
    }

    @Test
    void invalidRequestsIdsFiltersAndMissingVersionsCannotChangeCategories() throws Exception {
        Browser admin = admin();
        assertThat(admin.send("POST", API, Map.of()).statusCode()).isEqualTo(400);
        for (var field : List.of(Map.entry("categoryName", " "), Map.entry("categoryName", "x".repeat(101)),
            Map.entry("slug", ""), Map.entry("slug", "123"), Map.entry("slug", "not--valid"),
            Map.entry("slug", "not/valid"), Map.entry("slug", "123-456"), Map.entry("slug", "élèves"), Map.entry("slug", "x".repeat(121)),
            Map.entry("categoryImage", "../file.png"), Map.entry("categoryImage", "javascript:alert(1)"),
            Map.entry("categoryImage", "x".repeat(501)), Map.entry("expectedVersion", 0), Map.entry("categoryId", 1),
            Map.entry("productCount", 99), Map.entry("adminUserId", 999), Map.entry("role", "admin"), Map.entry("isVisible", "invalid"))) {
            Map<String, Object> request = new HashMap<>(Map.of("categoryName", "New category"));
            request.put(field.getKey(), field.getValue());
            assertThat(admin.send("POST", API, request).statusCode()).as("invalid field %s", field.getKey()).isEqualTo(400);
        }
        for (String id : List.of("0", "-1", "abc", "1.5", "9223372036854775808")) {
            assertThat(admin.get(API + "/" + id).statusCode()).isEqualTo(400);
            assertThat(admin.send("DELETE", API + "/" + id + "?expectedVersion=0", Map.of()).statusCode()).isEqualTo(400);
        }
        assertError(admin.get(API + "/999999"), 404, "ADMIN_CATEGORY_NOT_FOUND");
        assertThat(admin.get(API + "?visibility=unknown").statusCode()).isEqualTo(400);
        assertThat(admin.get(API + "?q=" + "x".repeat(256)).statusCode()).isEqualTo(400);
        assertThat(admin.send("PUT", API + "/1", Map.of("categoryName", "Changed")).statusCode()).isEqualTo(400);
        assertThat(admin.send("PUT", API + "/1/toggle", Map.of()).statusCode()).isEqualTo(400);
        assertThat(admin.send("PUT", API + "/1/visibility", Map.of("isVisible", false)).statusCode()).isEqualTo(400);
        assertThat(admin.send("DELETE", API + "/1", Map.of()).statusCode()).isEqualTo(400);
        assertThat(admin.send("DELETE", API + "/1?expectedVersion=-1", Map.of()).statusCode()).isEqualTo(400);
        assertThat(ok(admin.get(API)).size()).isEqualTo(5);
        assertThat(ok(admin.get(API + "/1")).get("version").asLong()).isZero();
    }

    @Test
    void staleEditsAndDeletionAreRejectedWithoutChangingTheCategory() throws Exception {
        Browser admin = admin();
        long id = created(admin.send("POST", API, Map.of("categoryName", "Empty"))).get("categoryId").asLong();
        JsonNode changed = ok(admin.send("PUT", API + "/" + id, Map.of("categoryName", "Updated", "expectedVersion", 0)));
        assertError(admin.send("PUT", API + "/" + id, Map.of("categoryName", "Stale", "expectedVersion", 0)), 409, "STALE_CATEGORY_VERSION");
        assertError(admin.send("DELETE", API + "/" + id + "?expectedVersion=0", Map.of()), 409, "STALE_CATEGORY_VERSION");
        assertThat(ok(admin.get(API + "/" + id))).isEqualTo(changed);
        assertThat(admin.send("DELETE", API + "/" + id + "?expectedVersion=1", Map.of()).statusCode()).isEqualTo(204);
    }

    @Test
    void guestCustomerAndMissingCsrfRequestsCannotManageCategories() throws Exception {
        Browser guest = new Browser(), customer = customer();
        assertThat(guest.get(API).statusCode()).isEqualTo(401);
        assertThat(guest.get(API + "/1").statusCode()).isEqualTo(401);
        assertThat(customer.get(API).statusCode()).isEqualTo(403);
        assertThat(customer.get(API + "/1").statusCode()).isEqualTo(403);
        assertThat(guest.send("POST", API, Map.of("categoryName", "New")).statusCode()).isEqualTo(401);
        assertThat(customer.send("POST", API, Map.of("categoryName", "New")).statusCode()).isEqualTo(403);
        for (String path : List.of("/1", "/1/toggle", "/1/visibility")) {
            assertThat(guest.send("PUT", API + path, Map.of("expectedVersion", 0)).statusCode()).isEqualTo(401);
            assertThat(customer.send("PUT", API + path, Map.of("expectedVersion", 0)).statusCode()).isEqualTo(403);
        }
        assertThat(customer.send("DELETE", API + "/1?expectedVersion=0", Map.of()).statusCode()).isEqualTo(403);
        Browser admin = admin();
        assertThat(admin.send("POST", API, Map.of("categoryName", "New"), null).statusCode()).isEqualTo(403);
        assertThat(admin.send("PUT", API + "/1/toggle", Map.of("expectedVersion", 0), null).statusCode()).isEqualTo(403);
        assertThat(admin.send("DELETE", API + "/1?expectedVersion=0", Map.of(), null).statusCode()).isEqualTo(403);
        assertThat(ok(admin.get(API)).size()).isEqualTo(5);
    }

    @Test
    void serviceAuthorizationAndValidationCannotBeBypassed() throws Exception {
        admin();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@example.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            assertThat(categories.list("admin@example.com", "", null).size()).isEqualTo(5);
            assertThatThrownBy(() -> categories.get("other@example.com", 1)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> categories.toggle("other@example.com", 1, new CategoryVersionRequest(0L))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> categories.create("admin@example.com", new CategoryWriteRequest(" ", null, null, null, null)))
                .isInstanceOf(ConstraintViolationException.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
            assertThatThrownBy(() -> categories.get("alice@example.com", 1)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> categories.delete("alice@example.com", 1, 0L)).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void concurrentEditsRejectOneStaleVersion() throws Exception {
        Browser first = admin(), second = adminLogin();
        var responses = concurrent(first, "PUT", API + "/1", Map.of("categoryName", "First", "expectedVersion", 0),
            second, "PUT", API + "/1", Map.of("categoryName", "Second", "expectedVersion", 0));
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200, 409);
        JsonNode category = ok(first.get(API + "/1"));
        assertThat(category.get("categoryName").asText()).isIn("First", "Second");
        assertThat(category.get("version").asLong()).isEqualTo(1);
    }

    @Test
    void concurrentSlugCreationCannotCreateDuplicates() throws Exception {
        Browser first = admin(), second = adminLogin();
        var responses = concurrent(first, "POST", API, Map.of("categoryName", "Same new category"),
            second, "POST", API, Map.of("categoryName", "Same new category"));
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM categories WHERE slug = 'same-new-category'", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentDeletionAndProductAssignmentPreserveReferentialIntegrity() throws Exception {
        Browser first = admin(), second = adminLogin();
        long id = created(first.send("POST", API, Map.of("categoryName", "Initially empty"))).get("categoryId").asLong();
        var responses = concurrent(first, "DELETE", API + "/" + id + "?expectedVersion=0", Map.of(),
            second, "POST", "/api/admin/products", Map.of("categoryId", id, "productName", "New book", "productPrice", 10));
        if (responses.get(0).statusCode() == 204) {
            assertThat(responses.get(1).statusCode()).as(responses.get(1).body()).isEqualTo(404);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE category_id = ?", Integer.class, id)).isZero();
        } else {
            assertThat(responses.get(0).statusCode()).as(responses.get(0).body()).isEqualTo(409);
            assertThat(responses.get(1).statusCode()).as(responses.get(1).body()).isEqualTo(201);
            assertThat(ok(first.get(API + "/" + id)).get("productCount").asInt()).isEqualTo(1);
        }
    }

    @Test
    void concurrentCheckoutAndCategoryHidingSerializeWithoutLosingStock() throws Exception {
        Browser admin = admin(), customer = customer();
        customer.add();
        var responses = concurrent(admin, "PUT", API + "/1/visibility", Map.of("isVisible", false, "expectedVersion", 0),
            customer, "POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER"));
        assertThat(responses.get(0).statusCode()).isEqualTo(200);
        int orderStatus = responses.get(1).statusCode();
        assertThat(orderStatus).as(responses.get(1).body()).isIn(201, 409);
        int stock = jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = 1", Integer.class);
        assertThat(stock).isEqualTo(orderStatus == 201 ? 73 : 74);
        assertThat(ok(admin.get(API + "/1")).get("isVisible").asBoolean()).isFalse();
        assertThat(ok(customer.get("/api/cart")).get("totalQuantity").asInt()).isEqualTo(orderStatus == 201 ? 0 : 1);
    }

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

    private Browser customer() throws Exception {
        Browser browser = new Browser();
        created(browser.send("POST", "/api/auth/register", Map.of("username", "Alice", "email", "alice@example.com", "password", "correct-password")));
        ok(browser.send("POST", "/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        created(browser.send("POST", "/api/account/addresses/add", Map.of("fullName", "Alice", "addressLine", "Street")));
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
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
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

        void add() throws Exception { ok(send("POST", "/api/cart/add", Map.of("productId", 1, "quantity", 1, "selectedFormat", "Paperback"))); }
    }
}
