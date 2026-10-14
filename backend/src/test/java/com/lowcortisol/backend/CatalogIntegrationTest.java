package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.catalog.CatalogDemoData;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "catalog-test-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> true);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired CatalogDemoData demo;
    @Autowired PasswordEncoder encoder;
    private Browser guest;

    @BeforeEach
    void prepareCatalog() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        demo.run(null);
        guest = new Browser();
    }

    @Test
    void publicListingHasStableFifteenItemPagesAndCorrectTotals() throws Exception {
        JsonNode first = guest.ok("/api/products");
        JsonNode second = guest.ok("/api/products?page=2");
        JsonNode last = guest.ok("/api/products?page=4");
        assertThat(first.get("page").asInt()).isEqualTo(1);
        assertThat(first.get("pageSize").asInt()).isEqualTo(15);
        assertThat(first.get("totalItems").asLong()).isEqualTo(60);
        assertThat(first.get("totalPages").asLong()).isEqualTo(4);
        assertThat(ids(first.get("items"))).hasSize(15).startsWith(60L).endsWith(46L);
        assertThat(ids(second.get("items"))).hasSize(15).startsWith(45L).endsWith(31L);
        assertThat(ids(last.get("items"))).hasSize(15).startsWith(15L).endsWith(1L);
        assertThat(first).isEqualTo(guest.ok("/api/products?page=1"));
        JsonNode beyond = guest.ok("/api/products?page=5");
        assertThat(beyond.get("items").size()).isZero();
        assertThat(beyond.get("totalItems").asLong()).isEqualTo(60);
        assertThat(guest.ok("/api/products?page=2147483647").get("items").size()).isZero();
    }

    @Test
    void featuredReturnsEightNewestVisibleProducts() throws Exception {
        assertThat(ids(guest.ok("/api/products/featured"))).containsExactly(60L, 59L, 58L, 57L, 56L, 55L, 54L, 53L);
        jdbc.update("UPDATE products SET is_visible = FALSE WHERE product_id = 60");
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 5");
        assertThat(ids(guest.ok("/api/products/featured"))).containsExactly(48L, 47L, 46L, 45L, 44L, 43L, 42L, 41L);
    }

    @Test
    void detailsReturnStructuredJsonPriceCategoryAndStock() throws Exception {
        JsonNode book = guest.ok("/api/products/1");
        assertThat(book.get("productName").asText()).isEqualTo("The Great Gatsby");
        assertThat(book.get("productPrice").decimalValue()).isEqualByComparingTo("18.00");
        assertThat(book.get("categoryName").asText()).isEqualTo("Fiction");
        assertThat(book.get("categorySlug").asText()).isEqualTo("fiction");
        assertThat(book.get("productImages").isArray()).isTrue();
        assertThat(book.get("productImages").get(0).asText()).isEqualTo("fiction-01.png");
        assertThat(book.get("productAttributes").get("author").asText()).isEqualTo("F. Scott Fitzgerald");
        assertThat(book.get("productAttributes").get("format").size()).isEqualTo(3);
        assertThat(book.get("inStock").asBoolean()).isTrue();
        assertThat(book.get("maxQuantity").asInt()).isEqualTo(10);
        assertThat(book.get("createdAt").asText()).isNotBlank();
    }

    @Test
    void hiddenProductsAndHiddenCategoriesAreExcludedEverywhere() throws Exception {
        jdbc.update("UPDATE products SET is_visible = FALSE WHERE product_id = 1");
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 2");
        assertThat(guest.get("/api/products/1").statusCode()).isEqualTo(404);
        assertThat(guest.get("/api/products/14").statusCode()).isEqualTo(404);
        assertThat(guest.get("/api/categories/children").statusCode()).isEqualTo(404);
        assertThat(guest.get("/api/categories/2/products").statusCode()).isEqualTo(404);
        assertThat(guest.ok("/api/categories").size()).isEqualTo(4);
        assertThat(guest.ok("/api/products").get("totalItems").asInt()).isEqualTo(47);
        assertThat(guest.ok(search("Gatsby")).get("totalItems").asInt()).isZero();
        assertThat(guest.ok(search("Harry")).get("totalItems").asInt()).isZero();
        assertThat(guest.ok("/api/categories/fiction/products").get("totalItems").asInt()).isEqualTo(11);
    }

    @Test
    void visibleZeroStockProductsRemainRetrievableWithAccurateAvailability() throws Exception {
        jdbc.update("UPDATE products SET product_quantity = 0 WHERE product_id = 1");
        JsonNode outOfStock = guest.ok("/api/products/1");
        assertThat(outOfStock.get("productQuantity").asInt()).isZero();
        assertThat(outOfStock.get("inStock").asBoolean()).isFalse();
        assertThat(outOfStock.get("maxQuantity").asInt()).isZero();
        assertThat(guest.ok(search("Gatsby")).get("totalItems").asInt()).isEqualTo(1);
        jdbc.update("UPDATE products SET product_quantity = 3 WHERE product_id = 1");
        assertThat(guest.ok("/api/products/1").get("maxQuantity").asInt()).isEqualTo(3);
    }

    @Test
    void searchMatchesTitlesDescriptionsAndAuthorsCaseInsensitively() throws Exception {
        assertThat(ids(guest.ok(search("hArRy PoTtEr")).get("items"))).containsExactly(14L);
        assertThat(ids(guest.ok(search("MYSTERIOUSLY WEALTHY")).get("items"))).containsExactly(1L);
        assertThat(ids(guest.ok(search("mUrAkAmI")).get("items"))).containsExactly(12L, 9L);
        assertThat(ids(guest.ok("/api/products?q=Murakami&category=fiction").get("items"))).containsExactly(12L, 9L);
        assertThat(guest.ok("/api/products/search?q=Murakami&category=children").get("totalItems").asInt()).isZero();
    }

    @Test
    void searchUsesPaginationAndHandlesEmptyQueriesAndNoResults() throws Exception {
        JsonNode first = guest.ok(search("Paperback"));
        // Format is not searched; only title, description, and author participate.
        assertThat(first.get("totalItems").asInt()).isZero();
        assertThat(guest.ok(search(" ")).get("totalItems").asInt()).isEqualTo(60);
        assertThat(guest.ok("/api/products/search")).isEqualTo(guest.ok("/api/products"));
        for (int index = 0; index < 20; index++) {
            jdbc.update("INSERT INTO products (category_id, product_name, product_price) VALUES (1, ?, 10.00)", "Searchable Book " + index);
        }
        JsonNode matches = guest.ok(search("Searchable"));
        assertThat(matches.get("items").size()).isEqualTo(15);
        assertThat(matches.get("totalItems").asInt()).isEqualTo(20);
        assertThat(matches.get("totalPages").asInt()).isEqualTo(2);
        assertThat(guest.ok(search("Searchable") + "&page=2").get("items").size()).isEqualTo(5);
        JsonNode missing = guest.ok(search("nothing-matches-this-query"));
        assertThat(missing.get("items").size()).isZero();
        assertThat(missing.get("totalPages").asInt()).isZero();
    }

    @Test
    void searchTreatsWildcardsLiterallyAndDoesNotInterpolateSql() throws Exception {
        jdbc.update("INSERT INTO products (category_id, product_name, product_price) VALUES (1, 'Save 100% Today', 10.00)");
        jdbc.update("INSERT INTO products (category_id, product_name, product_price) VALUES (1, 'Code_Review', 10.00)");
        jdbc.update("INSERT INTO products (category_id, product_name, product_price) VALUES (1, 'Hello! World', 10.00)");
        assertThat(guest.ok(search("%")).get("totalItems").asInt()).isEqualTo(1);
        assertThat(guest.ok(search("_")).get("totalItems").asInt()).isEqualTo(1);
        assertThat(guest.ok(search("!")).get("totalItems").asInt()).isEqualTo(1);
        assertThat(guest.ok(search("' OR 1=1 --")).get("totalItems").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(63);
    }

    @Test
    void categoriesResolveByIdAndLegacySlugAndAllowEmptyVisibleCategories() throws Exception {
        assertThat(guest.ok("/api/categories").size()).isEqualTo(5);
        assertThat(guest.ok("/api/categories/fiction")).isEqualTo(guest.ok("/api/categories/1"));
        assertThat(guest.ok("/api/categories/science").get("categoryId").asInt()).isEqualTo(5);
        assertThat(guest.ok("/api/categories/nonfiction").get("categoryId").asInt()).isEqualTo(3);
        assertThat(guest.ok("/api/categories/mystery").get("categoryId").asInt()).isEqualTo(4);
        assertThat(guest.ok("/api/categories/fiction/products")).isEqualTo(guest.ok("/api/products?category=1"));
        assertThat(guest.ok("/api/categories/fiction/products").get("totalItems").asInt()).isEqualTo(12);
        jdbc.update("INSERT INTO categories (category_name, slug) VALUES ('Empty Category', 'empty-category')");
        assertThat(guest.ok("/api/categories/empty-category/products").get("totalItems").asInt()).isZero();
    }

    @Test
    void invalidIdsUnknownCategoriesAndMalformedQueriesReturnStructuredErrors() throws Exception {
        for (String id : List.of("abc", "0", "-1", "999999999999999999999999999999", "999999")) {
            HttpResponse<String> response = guest.get("/api/products/" + id);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("CATALOG_NOT_FOUND");
        }
        for (String page : List.of("0", "-1", "abc", "1.5", "2147483648")) {
            HttpResponse<String> response = guest.get("/api/products?page=" + page);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("INVALID_CATALOG_REQUEST");
        }
        assertThat(guest.get(search("a".repeat(256))).statusCode()).isEqualTo(400);
        assertThat(guest.get("/api/products?category=missing-category").statusCode()).isEqualTo(404);
        assertThat(guest.get("/api/categories/9999999999999999999999999/products").statusCode()).isEqualTo(404);
    }

    @Test
    void publicCatalogRemainsAccessibleToCustomerAndAdminSessions() throws Exception {
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, 'customer')",
            "Customer", "customer@example.com", encoder.encode("correct-password"));
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, 'admin')",
            "Admin", "admin@example.com", encoder.encode("correct-password"));
        Browser customer = new Browser();
        assertThat(customer.post("/api/auth/login", Map.of("email", "customer@example.com", "password", "correct-password")).statusCode())
            .isEqualTo(200);
        Browser admin = new Browser();
        assertThat(admin.post("/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "correct-password")).statusCode())
            .isEqualTo(200);
        for (Browser browser : List.of(guest, customer, admin)) {
            assertThat(browser.ok("/api/products").get("totalItems").asInt()).isEqualTo(60);
            assertThat(browser.ok("/api/categories").size()).isEqualTo(5);
            assertThat(browser.ok("/api/products/1").get("productId").asInt()).isEqualTo(1);
        }
    }

    @Test
    void demoImportDoesNotOverwriteExistingCatalogAndIdentitySequencesContinue() throws Exception {
        jdbc.update("UPDATE products SET product_name = 'Edited Book', is_visible = FALSE WHERE product_id = 1");
        demo.run(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT product_name FROM products WHERE product_id = 1", String.class)).isEqualTo("Edited Book");
        assertThat(guest.get("/api/products/1").statusCode()).isEqualTo(404);
        long newId = jdbc.queryForObject("INSERT INTO products (category_id, product_name, product_price) VALUES (1, 'New Book', 10.00) RETURNING product_id", Long.class);
        assertThat(newId).isEqualTo(61);
        jdbc.update("DELETE FROM products");
        demo.run(null);
        assertThat(guest.ok("/api/products").get("totalItems").asInt()).isZero();
        assertThat(guest.ok("/api/products/featured").size()).isZero();
    }

    private List<Long> ids(JsonNode products) {
        List<Long> result = new ArrayList<>();
        products.forEach(product -> result.add(product.get("productId").asLong()));
        return result;
    }

    private String search(String query) {
        return "/api/products/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
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

        HttpResponse<String> post(String path, Object body) throws Exception {
            String token = ok("/api/auth/csrf").get("token").asText();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
