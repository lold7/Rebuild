package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.payment.AddPaymentMethodRequest;
import com.lowcortisol.backend.payment.PaymentMethodService;
import jakarta.validation.ConstraintViolationException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
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
import org.springframework.dao.DataIntegrityViolationException;
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
class PaymentMethodIntegrationTest {
    private static final String API = "/api/account/payment-methods";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "payment-test-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> false);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired PaymentMethodService methods;

    @BeforeEach
    void prepare() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void newCustomerHasEmptyMethodsAndNoDefault() throws Exception {
        JsonNode book = customer("alice@example.com").book();
        assertThat(book.get("totalMethods").asInt()).isZero();
        assertThat(book.get("paymentMethods").size()).isZero();
        assertThat(book.get("defaultPaymentMethodId").isNull()).isTrue();
    }

    @Test
    void firstCardBecomesDefaultAndResponseOnlyContainsDisplayMetadata() throws Exception {
        Browser browser = customer("alice@example.com");
        Map<String, Object> request = card(" credit ", "4242", false);
        request.put("brand", " VISA ");
        request.put("cardholderName", " Alice Example ");
        request.remove("currency");
        HttpResponse<String> response = browser.post(API + "/add", request);
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode method = json.readTree(response.body());
        assertThat(method.get("type").asText()).isEqualTo("CREDIT");
        assertThat(method.get("brand").asText()).isEqualTo("visa");
        assertThat(method.get("cardholderName").asText()).isEqualTo("Alice Example");
        assertThat(method.get("last4").asText()).isEqualTo("4242");
        assertThat(method.get("currency").asText()).isEqualTo("USD");
        assertThat(method.get("isDefault").asBoolean()).isTrue();
        assertThat(method.get("expired").asBoolean()).isFalse();
        assertThat(method.get("createdAt").asText()).isNotBlank();
        assertThat(method.has("userId")).isFalse();
        assertThat(method.has("cardNumber")).isFalse();
        assertThat(method.has("cvv")).isFalse();
        assertThat(ok(browser.get(response.headers().firstValue("Location").orElseThrow()))).isEqualTo(method);
        var columns = jdbc.queryForList("""
            SELECT column_name FROM information_schema.columns WHERE table_name = 'payment_methods'
            """, String.class);
        assertThat(columns).doesNotContain("card_number", "pan", "cvv", "cvc", "security_code");
    }

    @Test
    void typeFiltersAreCaseInsensitiveAndKeepTheAccountDefaultId() throws Exception {
        Browser browser = customer("alice@example.com");
        long credit = browser.add("CREDIT", "4242", false);
        long debit = browser.add("debit", "0001", false);
        JsonNode filtered = ok(browser.get(API + "?type=debit"));
        assertThat(filtered.get("totalMethods").asInt()).isEqualTo(1);
        assertThat(filtered.get("paymentMethods").get(0).get("paymentMethodId").asLong()).isEqualTo(debit);
        assertThat(filtered.get("defaultPaymentMethodId").asLong()).isEqualTo(credit);
        assertThat(ok(browser.get(API + "?type=CREDIT")).get("totalMethods").asInt()).isEqualTo(1);
        assertThat(browser.book().get("totalMethods").asInt()).isEqualTo(2);
        assertThat(browser.get(API + "?type=bank").statusCode()).isEqualTo(400);
        assertThat(browser.get(API + "?type=").statusCode()).isEqualTo(400);
    }

    @Test
    void defaultFirstOrderingAndIdempotentSelectionMaintainOneDefault() throws Exception {
        Browser browser = customer("alice@example.com");
        long first = browser.add("CREDIT", "4242", false);
        long second = browser.add("DEBIT", "1111", true);
        long third = browser.add("CREDIT", "2222", false);
        JsonNode book = browser.book();
        assertThat(book.get("defaultPaymentMethodId").asLong()).isEqualTo(second);
        assertThat(book.get("paymentMethods").get(0).get("paymentMethodId").asLong()).isEqualTo(second);
        assertThat(book.get("paymentMethods").get(1).get("paymentMethodId").asLong()).isEqualTo(third);
        JsonNode selected = ok(browser.post(API + "/default/" + first, Map.of()));
        assertThat(selected.get("defaultPaymentMethodId").asLong()).isEqualTo(first);
        assertThat(ok(browser.post(API + "/default/" + first, Map.of()))).isEqualTo(selected);
        assertOneDefault();
    }

    @Test
    void removalKeepsDefaultOrPromotesNewestActiveCardInsteadOfAnExpiredCard() throws Exception {
        Browser browser = customer("alice@example.com");
        long first = browser.add("CREDIT", "4242", false);
        long second = browser.add("CREDIT", "1111", false);
        long third = browser.add("CREDIT", "2222", false);
        long expired = browser.add("CREDIT", "3333", false);
        expire(expired);
        assertThat(ok(browser.post(API + "/delete/" + third, Map.of())).get("defaultPaymentMethodId").asLong()).isEqualTo(first);
        JsonNode book = ok(browser.post(API + "/delete/" + first, Map.of()));
        assertThat(book.get("defaultPaymentMethodId").asLong()).isEqualTo(second);
        assertThat(book.get("totalMethods").asInt()).isEqualTo(2);
        assertThat(browser.get(API + "/" + first).statusCode()).isEqualTo(404);
        assertOneDefault();
    }

    @Test
    void lastActiveCardCannotBeRemovedButExpiredCardsCanLeaveAnEmptyBook() throws Exception {
        Browser browser = customer("alice@example.com");
        long active = browser.add("CREDIT", "4242", false);
        long expired = browser.add("DEBIT", "1111", false);
        expire(expired);
        HttpResponse<String> rejected = browser.post(API + "/delete/" + active, Map.of());
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(json.readTree(rejected.body()).get("code").asText()).isEqualTo("LAST_ACTIVE_PAYMENT_METHOD");
        assertThat(browser.book().get("totalMethods").asInt()).isEqualTo(2);
        ok(browser.post(API + "/delete/" + expired, Map.of()));
        expire(active);
        JsonNode empty = ok(browser.post(API + "/delete/" + active, Map.of()));
        assertThat(empty.get("totalMethods").asInt()).isZero();
        assertThat(empty.get("defaultPaymentMethodId").isNull()).isTrue();
    }

    @Test
    void cardsRemainActiveThroughTheirExpiryMonthAndPastDatesAreRejected() throws Exception {
        Browser browser = customer("alice@example.com");
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        Map<String, Object> current = card("CREDIT", "4242", false);
        current.put("expiryMonth", now.getMonthValue());
        current.put("expiryYear", now.getYear());
        HttpResponse<String> response = browser.post(API + "/add", current);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json.readTree(response.body()).get("expired").asBoolean()).isFalse();
        current.put("expiryMonth", now.minusMonths(1).getMonthValue());
        current.put("expiryYear", now.minusMonths(1).getYear());
        current.put("isDefault", true);
        HttpResponse<String> rejected = browser.post(API + "/add", current);
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(json.readTree(rejected.body()).get("code").asText()).isEqualTo("PAYMENT_METHOD_EXPIRED");
        assertThat(browser.book().get("totalMethods").asInt()).isEqualTo(1);
        assertOneDefault();
    }

    @Test
    void expiryIsReportedAndExpiredDefaultIsReplacedWhenAddingAnActiveCard() throws Exception {
        Browser browser = customer("alice@example.com");
        long expired = browser.add("CREDIT", "4242", false);
        expire(expired);
        assertThat(ok(browser.get(API + "/" + expired)).get("expired").asBoolean()).isTrue();
        long active = browser.add("DEBIT", "1111", false);
        assertThat(browser.book().get("defaultPaymentMethodId").asLong()).isEqualTo(active);
        HttpResponse<String> rejected = browser.post(API + "/default/" + expired, Map.of());
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(json.readTree(rejected.body()).get("code").asText()).isEqualTo("PAYMENT_METHOD_EXPIRED");
        assertThat(browser.book().get("defaultPaymentMethodId").asLong()).isEqualTo(active);
        assertOneDefault();
    }

    @Test
    void foreignIdsAreIndistinguishableFromMissingIdsAndCannotChangeDefaults() throws Exception {
        Browser alice = customer("alice@example.com");
        long aliceId = alice.add("CREDIT", "4242", false);
        Browser bob = customer("bob@example.com");
        long bobId = bob.add("DEBIT", "1111", false);
        HttpResponse<String> foreign = alice.get(API + "/" + bobId);
        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(foreign.body()).isEqualTo(alice.get(API + "/999999").body());
        assertThat(alice.post(API + "/default/" + bobId, Map.of()).statusCode()).isEqualTo(404);
        assertThat(alice.post(API + "/delete/" + bobId, Map.of()).statusCode()).isEqualTo(404);
        assertThat(alice.book().get("totalMethods").asInt()).isEqualTo(1);
        assertThat(alice.book().get("defaultPaymentMethodId").asLong()).isEqualTo(aliceId);
        assertThat(bob.book().get("defaultPaymentMethodId").asLong()).isEqualTo(bobId);
    }

    @Test
    void invalidInputsOwnershipInjectionAndRawCardFieldsAreRejected() throws Exception {
        Browser browser = customer("alice@example.com");
        long id = browser.add("CREDIT", "4242", false);
        assertThat(browser.post(API + "/add", Map.of()).statusCode()).isEqualTo(400);
        for (var invalid : List.of(Map.entry("type", "bank"), Map.entry("brand", "invalid"),
            Map.entry("cardholderName", " "), Map.entry("cardholderName", "a".repeat(201)),
            Map.entry("last4", "123"), Map.entry("last4", "4242424242424242"), Map.entry("last4", "abcd"),
            Map.entry("expiryMonth", 0), Map.entry("expiryMonth", 13), Map.entry("expiryMonth", 1.5),
            Map.entry("expiryYear", 10000), Map.entry("currency", "ZZZ"), Map.entry("currency", "US"),
            Map.entry("currency", ""), Map.entry("userId", 999), Map.entry("isDefault", "invalid"),
            Map.entry("role", "admin"), Map.entry("cardNumber", "4242424242424242"), Map.entry("cvv", "123"),
            Map.entry("cvc", "123"), Map.entry("token", "unverified-token"))) {
            Map<String, Object> request = card("CREDIT", "1111", true);
            request.put(invalid.getKey(), invalid.getValue());
            HttpResponse<String> response = browser.post(API + "/add", request);
            assertThat(response.statusCode()).as("invalid field %s", invalid.getKey()).isEqualTo(400);
            assertThat(response.body()).doesNotContain("4242424242424242", "unverified-token");
        }
        assertThat(browser.book().get("totalMethods").asInt()).isEqualTo(1);
        assertThat(browser.book().get("defaultPaymentMethodId").asLong()).isEqualTo(id);
    }

    @Test
    void malformedAndMissingIdsDoNotChangeSavedMethods() throws Exception {
        Browser browser = customer("alice@example.com");
        long id = browser.add("CREDIT", "4242", false);
        for (String invalid : List.of("0", "-1", "abc", "1.5", "9223372036854775808")) {
            assertThat(browser.get(API + "/" + invalid).statusCode()).isEqualTo(400);
            assertThat(browser.post(API + "/default/" + invalid, Map.of()).statusCode()).isEqualTo(400);
            assertThat(browser.post(API + "/delete/" + invalid, Map.of()).statusCode()).isEqualTo(400);
        }
        assertThat(browser.post(API + "/default/999999", Map.of()).statusCode()).isEqualTo(404);
        assertThat(browser.post(API + "/delete/999999", Map.of()).statusCode()).isEqualTo(404);
        assertThat(browser.book().get("defaultPaymentMethodId").asLong()).isEqualTo(id);
    }

    @Test
    void guestsAdminsAndRequestsWithoutCsrfAreBlocked() throws Exception {
        Browser guest = new Browser();
        assertThat(guest.get(API).statusCode()).isEqualTo(401);
        assertThat(guest.get(API + "/1").statusCode()).isEqualTo(401);
        for (String path : List.of("/add", "/delete/1", "/default/1")) {
            assertThat(guest.post(API + path, card("CREDIT", "4242", false)).statusCode()).isEqualTo(401);
        }
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, 'admin')",
            "Admin", "admin@example.com", encoder.encode("correct-password"));
        Browser admin = new Browser();
        ok(admin.post("/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "correct-password")));
        assertThat(admin.get(API).statusCode()).isEqualTo(403);
        assertThat(admin.get(API + "/1").statusCode()).isEqualTo(403);
        for (String path : List.of("/add", "/delete/1", "/default/1")) {
            assertThat(admin.post(API + path, card("CREDIT", "4242", false)).statusCode()).isEqualTo(403);
        }
        Browser browser = customer("alice@example.com");
        long id = browser.add("CREDIT", "4242", false);
        for (String path : List.of("/add", "/delete/" + id, "/default/" + id)) {
            assertThat(browser.send(API + path, card("CREDIT", "1111", true), null).statusCode()).isEqualTo(403);
        }
        assertThat(browser.book().get("totalMethods").asInt()).isEqualTo(1);
    }

    @Test
    void paymentMethodsPersistAcrossLogoutEmailAndPasswordChangesAndCascadeOnAccountDeletion() throws Exception {
        Browser browser = customer("alice@example.com");
        long id = browser.add("CREDIT", "4242", false);
        assertThat(browser.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
        ok(browser.post("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        assertThat(browser.book().get("defaultPaymentMethodId").asLong()).isEqualTo(id);
        ok(browser.post("/api/account/profile", Map.of("username", "Alice", "email", "new@example.com")));
        ok(browser.post("/api/auth/login", Map.of("email", "new@example.com", "password", "correct-password")));
        ok(browser.post("/api/account/password", Map.of("currentPassword", "correct-password", "newPassword", "changed-password", "confirmPassword", "changed-password")));
        ok(browser.post("/api/auth/login", Map.of("email", "new@example.com", "password", "changed-password")));
        assertThat(browser.book().get("defaultPaymentMethodId").asLong()).isEqualTo(id);
        jdbc.update("DELETE FROM users WHERE email = 'new@example.com'");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_methods", Integer.class)).isZero();
    }

    @Test
    void serviceAuthorizationAndValidationCannotBeBypassed() throws Exception {
        customer("alice@example.com");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        try {
            assertThat(methods.list("alice@example.com", null).totalMethods()).isZero();
            assertThatThrownBy(() -> methods.list("other@example.com", null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> methods.setDefault("other@example.com", 1)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> methods.delete("other@example.com", 1)).isInstanceOf(AccessDeniedException.class);
            var invalid = new AddPaymentMethodRequest("CREDIT", "visa", "Alice", "4242424242424242", 13, 2030, "USD", false);
            assertThatThrownBy(() -> methods.add("alice@example.com", invalid)).isInstanceOf(ConstraintViolationException.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            assertThatThrownBy(() -> methods.get("alice@example.com", 1)).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void databaseRejectsInvalidMetadataAndMultipleDefaults() throws Exception {
        Browser browser = customer("alice@example.com");
        browser.add("CREDIT", "4242", false);
        long second = browser.add("DEBIT", "1111", false);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_methods SET is_default = TRUE WHERE payment_method_id = ?", second))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_methods SET expiry_month = 13 WHERE payment_method_id = ?", second))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_methods SET last4 = 'abcd' WHERE payment_method_id = ?", second))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_methods SET type = 'BANK' WHERE payment_method_id = ?", second))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(browser.book().get("totalMethods").asInt()).isEqualTo(2);
        assertOneDefault();
    }

    @Test
    void concurrentFirstAddsAndDefaultSelectionsKeepOneDefault() throws Exception {
        Browser first = customer("alice@example.com");
        Browser second = new Browser();
        ok(second.post("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        var created = concurrent(first, "/add", card("CREDIT", "4242", false), second, "/add", card("DEBIT", "1111", false));
        assertThat(created).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(201));
        long firstId = json.readTree(created.get(0).body()).get("paymentMethodId").asLong();
        long secondId = json.readTree(created.get(1).body()).get("paymentMethodId").asLong();
        assertOneDefault();
        var selected = concurrent(first, "/default/" + firstId, Map.of(), second, "/default/" + secondId, Map.of());
        assertThat(selected).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        assertThat(first.book().get("defaultPaymentMethodId").asLong()).isIn(firstId, secondId);
        assertOneDefault();
    }

    @Test
    void concurrentRemovalsCannotRemoveTheLastActiveCard() throws Exception {
        Browser first = customer("alice@example.com");
        Browser second = new Browser();
        ok(second.post("/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password")));
        long firstId = first.add("CREDIT", "4242", false);
        long secondId = first.add("DEBIT", "1111", false);
        var responses = concurrent(first, "/delete/" + firstId, Map.of(), second, "/delete/" + secondId, Map.of());
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200, 409);
        assertThat(first.book().get("totalMethods").asInt()).isEqualTo(1);
        assertOneDefault();
    }

    private Map<String, Object> card(String type, String last4, boolean isDefault) {
        YearMonth expiry = YearMonth.now(ZoneOffset.UTC).plusYears(2);
        return new HashMap<>(Map.of("type", type, "brand", "visa", "cardholderName", "Alice Example", "last4", last4,
            "expiryMonth", expiry.getMonthValue(), "expiryYear", expiry.getYear(), "currency", "usd", "isDefault", isDefault));
    }

    private void expire(long id) {
        YearMonth expired = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        jdbc.update("UPDATE payment_methods SET expiry_month = ?, expiry_year = ? WHERE payment_method_id = ?",
            expired.getMonthValue(), expired.getYear(), id);
    }

    private void assertOneDefault() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_methods WHERE is_default", Integer.class)).isEqualTo(1);
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
                return first.send(API + firstPath, firstBody, firstToken);
            });
            var two = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return second.send(API + secondPath, secondBody, secondToken);
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
        assertThat(browser.post("/api/auth/register", Map.of("username", "Customer", "email", email, "password", "correct-password")).statusCode()).isEqualTo(201);
        ok(browser.post("/api/auth/login", Map.of("email", email, "password", "correct-password")));
        return browser;
    }

    private class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        JsonNode book() throws Exception { return ok(get(API)); }
        String csrf() throws Exception { return ok(get("/api/auth/csrf")).get("token").asText(); }

        long add(String type, String last4, boolean isDefault) throws Exception {
            HttpResponse<String> response = post(API + "/add", card(type, last4, isDefault));
            assertThat(response.statusCode()).isEqualTo(201);
            return json.readTree(response.body()).get("paymentMethodId").asLong();
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
