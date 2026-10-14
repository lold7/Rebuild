package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.address.AddAddressRequest;
import com.lowcortisol.backend.address.AddressService;
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
class AddressIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "address-test-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> false);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AddressService addresses;

    @BeforeEach
    void prepare() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void newCustomerHasEmptyAddressBook() throws Exception {
        JsonNode book = customer("alice@example.com").book();
        assertThat(book.get("addresses").size()).isZero();
        assertThat(book.get("totalAddresses").asInt()).isZero();
        assertThat(book.get("defaultAddressId").isNull()).isTrue();
    }

    @Test
    void firstAddressBecomesDefaultAndAppliesOriginalFieldDefaults() throws Exception {
        Browser customer = customer("alice@example.com");
        HttpResponse<String> response = customer.post("/api/account/addresses/add",
            Map.of("fullName", " Alice Example ", "addressLine", " 123 Main Street "));
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode address = json.readTree(response.body());
        assertThat(address.get("isDefault").asBoolean()).isTrue();
        assertThat(address.get("label").asText()).isEqualTo("Home");
        assertThat(address.get("fullName").asText()).isEqualTo("Alice Example");
        assertThat(address.get("addressLine").asText()).isEqualTo("123 Main Street");
        assertThat(address.get("phone").isNull()).isTrue();
        assertThat(address.get("city").asText()).isEqualTo("Bangkok");
        assertThat(address.get("province").asText()).isEqualTo("Bangkok");
        assertThat(address.get("postalCode").asText()).isEqualTo("10110");
        assertThat(address.get("createdAt").asText()).isNotBlank();
        assertThat(address.has("userId")).isFalse();
        assertThat(json.readTree(customer.get(response.headers().firstValue("Location").orElseThrow()).body())).isEqualTo(address);
    }

    @Test
    void addingDefaultAndSelectingDefaultKeepExactlyOneAndSortDefaultFirst() throws Exception {
        Browser customer = customer("alice@example.com");
        long first = customer.add("First", false);
        long second = customer.add("Second", true);
        long third = customer.add("Third", false);
        JsonNode book = customer.book();
        assertThat(book.get("defaultAddressId").asLong()).isEqualTo(second);
        assertThat(book.get("addresses").get(0).get("addressId").asLong()).isEqualTo(second);
        assertThat(book.get("addresses").get(1).get("addressId").asLong()).isEqualTo(third);
        assertThat(book.get("addresses").get(2).get("addressId").asLong()).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM addresses WHERE is_default", Integer.class)).isEqualTo(1);
        JsonNode chosen = ok(customer.post("/api/account/addresses/default/" + first, Map.of()));
        assertThat(chosen.get("defaultAddressId").asLong()).isEqualTo(first);
        assertThat(chosen.get("addresses").get(0).get("addressId").asLong()).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM addresses WHERE is_default", Integer.class)).isEqualTo(1);
        assertThat(ok(customer.post("/api/account/addresses/default/" + first, Map.of()))).isEqualTo(chosen);
    }

    @Test
    void deletingNonDefaultKeepsCurrentDefault() throws Exception {
        Browser customer = customer("alice@example.com");
        long first = customer.add("First", false);
        long second = customer.add("Second", false);
        JsonNode book = ok(customer.post("/api/account/addresses/delete/" + second, Map.of()));
        assertThat(book.get("totalAddresses").asInt()).isEqualTo(1);
        assertThat(book.get("defaultAddressId").asLong()).isEqualTo(first);
        assertThat(customer.get("/api/account/addresses/" + second).statusCode()).isEqualTo(404);
    }

    @Test
    void deletingDefaultPromotesNewestRemainingAndDeletingLastLeavesEmptyBook() throws Exception {
        Browser customer = customer("alice@example.com");
        long first = customer.add("First", false);
        long second = customer.add("Second", false);
        long third = customer.add("Third", false);
        assertThat(ok(customer.post("/api/account/addresses/delete/" + first, Map.of())).get("defaultAddressId").asLong()).isEqualTo(third);
        assertThat(ok(customer.post("/api/account/addresses/delete/" + third, Map.of())).get("defaultAddressId").asLong()).isEqualTo(second);
        JsonNode empty = ok(customer.post("/api/account/addresses/delete/" + second, Map.of()));
        assertThat(empty.get("addresses").size()).isZero();
        assertThat(empty.get("defaultAddressId").isNull()).isTrue();
        long replacement = customer.add("Replacement", false);
        assertThat(customer.book().get("defaultAddressId").asLong()).isEqualTo(replacement);
    }

    @Test
    void foreignIdsCannotReadDeleteOrClearAnotherCustomersDefault() throws Exception {
        Browser first = customer("first@example.com");
        Browser second = customer("second@example.com");
        long firstId = first.add("First", false);
        long secondId = second.add("Second", false);
        HttpResponse<String> foreign = first.get("/api/account/addresses/" + secondId);
        HttpResponse<String> missing = first.get("/api/account/addresses/999999");
        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(foreign.body()).isEqualTo(missing.body());
        assertThat(first.post("/api/account/addresses/default/" + secondId, Map.of()).statusCode()).isEqualTo(404);
        assertThat(first.post("/api/account/addresses/delete/" + secondId, Map.of()).statusCode()).isEqualTo(404);
        assertThat(first.book().get("defaultAddressId").asLong()).isEqualTo(firstId);
        assertThat(second.book().get("defaultAddressId").asLong()).isEqualTo(secondId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM addresses", Integer.class)).isEqualTo(2);
    }

    @Test
    void validationAndOwnershipInjectionAreRejectedWithoutChangingDefault() throws Exception {
        Browser customer = customer("alice@example.com");
        long id = customer.add("Original", false);
        for (Object body : List.of(Map.of(), Map.of("fullName", " ", "addressLine", "Street", "isDefault", true),
            Map.of("fullName", "Alice", "addressLine", " "), Map.of("fullName", "a".repeat(201), "addressLine", "Street"),
            Map.of("fullName", "Alice", "addressLine", "a".repeat(501)),
            Map.of("fullName", "Alice", "addressLine", "Street", "label", "a".repeat(51)),
            Map.of("fullName", "Alice", "addressLine", "Street", "phone", "1".repeat(21)),
            Map.of("fullName", "Alice", "addressLine", "Street", "city", "a".repeat(101)),
            Map.of("fullName", "Alice", "addressLine", "Street", "province", "a".repeat(101)),
            Map.of("fullName", "Alice", "addressLine", "Street", "postalCode", "1".repeat(11)),
            Map.of("fullName", "Alice", "addressLine", "Street", "userId", 999),
            Map.of("fullName", "Alice", "addressLine", "Street", "role", "admin"))) {
            assertThat(customer.post("/api/account/addresses/add", body).statusCode()).isEqualTo(400);
        }
        assertThat(customer.book().get("totalAddresses").asInt()).isEqualTo(1);
        assertThat(customer.book().get("defaultAddressId").asLong()).isEqualTo(id);
    }

    @Test
    void malformedAndMissingIdsLeaveAddressBookUnchanged() throws Exception {
        Browser customer = customer("alice@example.com");
        long id = customer.add("Original", false);
        for (String invalid : List.of("0", "-1", "abc", "9223372036854775808")) {
            assertThat(customer.get("/api/account/addresses/" + invalid).statusCode()).isEqualTo(400);
            assertThat(customer.post("/api/account/addresses/default/" + invalid, Map.of()).statusCode()).isEqualTo(400);
            assertThat(customer.post("/api/account/addresses/delete/" + invalid, Map.of()).statusCode()).isEqualTo(400);
        }
        assertThat(customer.post("/api/account/addresses/default/999999", Map.of()).statusCode()).isEqualTo(404);
        assertThat(customer.post("/api/account/addresses/delete/999999", Map.of()).statusCode()).isEqualTo(404);
        assertThat(customer.book().get("defaultAddressId").asLong()).isEqualTo(id);
    }

    @Test
    void guestsAdminsAndRequestsWithoutCsrfAreBlocked() throws Exception {
        Browser guest = new Browser();
        assertThat(guest.get("/api/account/addresses").statusCode()).isEqualTo(401);
        assertThat(guest.get("/api/account/addresses/1").statusCode()).isEqualTo(401);
        for (String path : List.of("/add", "/delete/1", "/default/1")) {
            assertThat(guest.post("/api/account/addresses" + path, Map.of()).statusCode()).isEqualTo(401);
        }
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, 'admin')",
            "Admin", "admin@example.com", encoder.encode("correct-password"));
        Browser admin = new Browser();
        ok(admin.post("/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "correct-password")));
        // Admins can use the storefront with their own address book.
        assertThat(admin.get("/api/account/addresses").statusCode()).isEqualTo(200);
        for (String path : List.of("/add", "/delete/1", "/default/1")) {
            assertThat(admin.post("/api/account/addresses" + path, Map.of()).statusCode()).isNotIn(401, 403);
        }
        Browser customer = customer("alice@example.com");
        long id = customer.add("Original", false);
        for (String path : List.of("/add", "/delete/" + id, "/default/" + id)) {
            assertThat(customer.send("/api/account/addresses" + path, address("New", true), null).statusCode()).isEqualTo(403);
        }
        assertThat(customer.book().get("totalAddresses").asInt()).isEqualTo(1);
    }

    @Test
    void addressBookPersistsAcrossLogoutEmailAndPasswordChanges() throws Exception {
        Browser customer = customer("alice@example.com");
        long id = customer.add("Home", false);
        assertThat(customer.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
        ok(customer.post("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        assertThat(customer.book().get("defaultAddressId").asLong()).isEqualTo(id);
        ok(customer.post("/api/account/profile", Map.of("username", "Alice", "email", "new@example.com")));
        ok(customer.post("/api/auth/login", Map.of("email", "new@example.com", "password", "correct-password")));
        ok(customer.post("/api/account/password", Map.of("currentPassword", "correct-password", "newPassword", "changed-password", "confirmPassword", "changed-password")));
        ok(customer.post("/api/auth/login", Map.of("email", "new@example.com", "password", "changed-password")));
        assertThat(customer.book().get("defaultAddressId").asLong()).isEqualTo(id);
        jdbc.update("DELETE FROM users WHERE email = 'new@example.com'");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM addresses", Integer.class)).isZero();
    }

    @Test
    void serviceAuthorizationBindsOperationsToCustomerIdentity() throws Exception {
        customer("alice@example.com");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        try {
            assertThat(addresses.list("alice@example.com").totalAddresses()).isZero();
            assertThatThrownBy(() -> addresses.list("other@example.com")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> addresses.setDefault("other@example.com", 1)).isInstanceOf(AccessDeniedException.class);
            // Admins may shop on the storefront, so only roles other than CUSTOMER/ADMIN are rejected.
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_GUEST"))));
            assertThatThrownBy(() -> addresses.add("alice@example.com", new AddAddressRequest("Home", "Alice", null, "Street", null, null, null, false)))
                .isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void concurrentFirstAddressesAndDefaultChangesMaintainOneDefault() throws Exception {
        Browser first = customer("alice@example.com");
        Browser second = new Browser();
        ok(second.post("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        var created = concurrent(first, "/api/account/addresses/add", address("First", false),
            second, "/api/account/addresses/add", address("Second", false));
        assertThat(created).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(201));
        long firstId = json.readTree(created.get(0).body()).get("addressId").asLong();
        long secondId = json.readTree(created.get(1).body()).get("addressId").asLong();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM addresses WHERE is_default", Integer.class)).isEqualTo(1);
        var defaults = concurrent(first, "/api/account/addresses/default/" + firstId, Map.of(),
            second, "/api/account/addresses/default/" + secondId, Map.of());
        assertThat(defaults).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        assertThat(first.book().get("totalAddresses").asInt()).isEqualTo(2);
        assertThat(first.book().get("defaultAddressId").asLong()).isIn(firstId, secondId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM addresses WHERE is_default", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentDeletionAndDefaultSelectionPreserveTheSelectedRemainingAddress() throws Exception {
        Browser first = customer("alice@example.com");
        Browser second = new Browser();
        ok(second.post("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        long firstId = first.add("First", false);
        long secondId = first.add("Second", false);
        first.add("Third", false);
        var responses = concurrent(first, "/api/account/addresses/delete/" + firstId, Map.of(),
            second, "/api/account/addresses/default/" + secondId, Map.of());
        assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        assertThat(first.book().get("defaultAddressId").asLong()).isEqualTo(secondId);
        assertThat(first.book().get("totalAddresses").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM addresses WHERE is_default", Integer.class)).isEqualTo(1);
    }

    private List<HttpResponse<String>> concurrent(Browser first, String firstPath, Object firstBody,
        Browser second, String secondPath, Object secondBody) throws Exception {
        String firstToken = first.csrf();
        String secondToken = second.csrf();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return first.send(firstPath, firstBody, firstToken);
            });
            var two = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return second.send(secondPath, secondBody, secondToken);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Map<String, Object> address(String label, boolean isDefault) {
        return Map.of("label", label, "fullName", "Alice Example", "phone", "0812345678", "addressLine", "123 Main Street",
            "city", "Chiang Mai", "province", "Chiang Mai", "postalCode", "50000", "isDefault", isDefault);
    }

    private JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private Browser customer(String email) throws Exception {
        Browser browser = new Browser();
        assertThat(browser.post("/api/auth/register", Map.of("username", "Customer", "email", email, "password", "correct-password")).statusCode()).isEqualTo(201);
        ok(browser.post("/api/auth/login", Map.of("email", email, "password", "correct-password")));
        return browser;
    }

    private class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        JsonNode book() throws Exception { return ok(get("/api/account/addresses")); }
        String csrf() throws Exception { return ok(get("/api/auth/csrf")).get("token").asText(); }

        long add(String label, boolean isDefault) throws Exception {
            HttpResponse<String> response = post("/api/account/addresses/add", address(label, isDefault));
            assertThat(response.statusCode()).isEqualTo(201);
            return json.readTree(response.body()).get("addressId").asLong();
        }

        HttpResponse<String> post(String path, Object body) throws Exception { return send(path, body, csrf()); }

        HttpResponse<String> send(String path, Object body, String token) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
