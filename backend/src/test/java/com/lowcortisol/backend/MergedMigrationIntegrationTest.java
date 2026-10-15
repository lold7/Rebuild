package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MergedMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        // Reproduce the address branch's schema history, where V6 exists but V5 does not.
        var legacyMigrations = Files.createTempDirectory("address-branch-migrations-");
        try {
            for (String migration : List.of("V1__customer_authentication.sql", "V2__password_recovery.sql",
                "V3__product_catalog.sql", "V4__wishlists.sql", "V6__address_book.sql")) {
                try (var input = new ClassPathResource("db/migration/" + migration).getInputStream()) {
                    Files.copy(input, legacyMigrations.resolve(migration));
                }
            }
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:" + legacyMigrations).load().migrate();
        } finally {
            try (var paths = Files.walk(legacyMigrations)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }

        var legacy = new JdbcTemplate(new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        long customerId = legacy.queryForObject("""
            INSERT INTO users (username, email, password_hash)
            VALUES ('Existing Customer', 'existing@example.com', ?) RETURNING user_id
            """, Long.class, new BCryptPasswordEncoder().encode("correct-password"));
        legacy.update("""
            INSERT INTO addresses (user_id, full_name, address_line, is_default)
            VALUES (?, 'Existing Customer', 'Original address', TRUE)
            """, customerId);
        legacy.update("INSERT INTO categories (category_name, slug) VALUES ('Books', 'books')");
        legacy.update("""
            INSERT INTO products (category_id, product_name, product_price, product_quantity, product_attributes)
            SELECT category_id, 'Existing book', 10.00, 3, '{"format":["Paperback"]}'::jsonb FROM categories
            """);

        // Without the merge fix, Flyway rejects the older cart migration on startup.
        var strictFlyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration").outOfOrder(false).load();
        assertThatThrownBy(strictFlyway::validate)
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Detected resolved migration not applied to database: 5");

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "merged-test-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> false);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired Flyway flyway;

    @Test
    void addressBranchUpgradesWithoutDataLossAndBothFeaturesWorkInOneSession() throws Exception {
        flyway.validate();
        int cartRank = jdbc.queryForObject(
            "SELECT installed_rank FROM flyway_schema_history WHERE version = '5' AND success", Integer.class);
        int addressRank = jdbc.queryForObject(
            "SELECT installed_rank FROM flyway_schema_history WHERE version = '6' AND success", Integer.class);
        assertThat(cartRank).isGreaterThan(addressRank);

        Browser browser = new Browser();
        ok(browser.post("/api/auth/login", Map.of("email", "existing@example.com", "password", "correct-password")));
        JsonNode originalBook = ok(browser.get("/api/account/addresses"));
        assertThat(originalBook.get("totalAddresses").asInt()).isEqualTo(1);
        JsonNode originalAddress = originalBook.get("addresses").get(0);
        assertThat(originalAddress.get("addressLine").asText()).isEqualTo("Original address");
        assertThat(originalAddress.get("isDefault").asBoolean()).isTrue();

        long productId = jdbc.queryForObject("SELECT product_id FROM products", Long.class);
        JsonNode cart = ok(browser.post("/api/cart/add",
            Map.of("productId", productId, "quantity", 2, "selectedFormat", "Paperback")));
        assertThat(cart.get("totalQuantity").asInt()).isEqualTo(2);
        assertThat(cart.get("totalPrice").decimalValue()).isEqualByComparingTo("20.00");

        HttpResponse<String> added = browser.post("/api/account/addresses/add",
            Map.of("fullName", "Existing Customer", "addressLine", "New address", "isDefault", true));
        assertThat(added.statusCode()).isEqualTo(201);
        long newAddressId = json.readTree(added.body()).get("addressId").asLong();
        JsonNode updatedBook = ok(browser.get("/api/account/addresses"));
        assertThat(updatedBook.get("totalAddresses").asInt()).isEqualTo(2);
        assertThat(updatedBook.get("defaultAddressId").asLong()).isEqualTo(newAddressId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM addresses WHERE is_default", Integer.class)).isEqualTo(1);
        assertThat(ok(browser.get("/api/cart")).get("totalQuantity").asInt()).isEqualTo(2);

        // Both exception handlers must remain registered after resolving the merge.
        assertThat(browser.post("/api/cart/remove", Map.of("cartItemId", 999999)).statusCode()).isEqualTo(404);
        assertThat(browser.post("/api/account/addresses/default/999999", Map.of()).statusCode()).isEqualTo(404);
    }

    private JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private class Browser {
        final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, Object body) throws Exception {
            String token = ok(get("/api/auth/csrf")).get("token").asText();
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        }
    }
}
