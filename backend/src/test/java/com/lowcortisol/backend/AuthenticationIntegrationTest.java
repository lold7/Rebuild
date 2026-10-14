package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.auth.AdminRegisterRequest;
import com.lowcortisol.backend.auth.AdminService;
import com.lowcortisol.backend.auth.BootstrapAdmin;
import com.lowcortisol.backend.auth.PasswordResetDelivery;
import com.lowcortisol.backend.auth.PasswordResetDeliveryException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "integration-test-secret-at-least-32-characters");
        registry.add("app.bootstrap.admin.enabled", () -> true);
        registry.add("app.bootstrap.admin.username", () -> "Bootstrap Admin");
        registry.add("app.bootstrap.admin.email", () -> "bootstrap@example.com");
        registry.add("app.bootstrap.admin.password", () -> "bootstrap-password");
        registry.add("app.password-reset.delivery", () -> "demo");
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AdminService admins;
    @MockitoSpyBean PasswordResetDelivery delivery;

    @BeforeEach
    void cleanDatabase() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void resetRequestStoresOnlyHashAndExpiresAfterThirtyMinutes() throws Exception {
        Browser browser = registeredBrowser();
        String token = requestReset(browser, "ALICE@example.com");
        assertThat(token).matches("[a-f0-9]{64}");
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject("SELECT token_hash FROM password_resets", String.class)).isEqualTo(expectedHash).isNotEqualTo(token);
        OffsetDateTime expires = jdbc.queryForObject("SELECT expires_at FROM password_resets", (rs, row) -> rs.getObject(1, OffsetDateTime.class));
        assertThat(expires.toInstant()).isBetween(Instant.now().plusSeconds(29 * 60), Instant.now().plusSeconds(31 * 60));
        assertThat(browser.get(resetPath(token)).statusCode()).isEqualTo(200);
        assertThat(browser.get(resetPath(token)).statusCode()).isEqualTo(200); // Viewing never consumes the token.
    }

    @Test
    void resetRequestUsesGenericMessageAndCreatesNoTokenForUnknownEmail() throws Exception {
        Browser browser = registeredBrowser();
        HttpResponse<String> known = browser.post("/api/auth/forgot-password", Map.of("email", "alice@example.com"));
        HttpResponse<String> unknown = browser.post("/api/auth/forgot-password", Map.of("email", "unknown@example.com"));
        assertThat(known.statusCode()).isEqualTo(200);
        assertThat(unknown.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(known.body()).get("message")).isEqualTo(mapper.readTree(unknown.body()).get("message"));
        assertThat(mapper.readTree(unknown.body()).has("resetLink")).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_resets", Integer.class)).isEqualTo(1);
    }

    @Test
    void resettingPasswordChangesHashAndMakesLinkSingleUse() throws Exception {
        Browser browser = registeredBrowser();
        String token = requestReset(browser, "alice@example.com");
        assertThat(browser.post(resetPath(token), resetCredentials("new-customer-password")).statusCode()).isEqualTo(200);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(encoder.matches("new-customer-password", hash)).isTrue();
        assertThat(encoder.matches("correct-password", hash)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_resets", Integer.class)).isZero();
        assertThat(browser.get(resetPath(token)).statusCode()).isEqualTo(400);
        assertThat(browser.post(resetPath(token), resetCredentials("another-password")).statusCode()).isEqualTo(400);
        assertThat(browser.post("/api/auth/login", login(false)).statusCode()).isEqualTo(401);
        assertThat(browser.post("/api/auth/login", Map.of("email", "alice@example.com", "password", "new-customer-password")).statusCode())
            .isEqualTo(200);
    }

    @Test
    void requestingAnotherLinkInvalidatesPreviousLink() throws Exception {
        Browser browser = registeredBrowser();
        String first = requestReset(browser, "alice@example.com");
        String second = requestReset(browser, "alice@example.com");
        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_resets", Integer.class)).isEqualTo(1);
        assertThat(browser.get(resetPath(first)).statusCode()).isEqualTo(400);
        assertThat(browser.post(resetPath(first), resetCredentials("new-customer-password")).statusCode()).isEqualTo(400);
        assertThat(browser.post(resetPath(second), resetCredentials("new-customer-password")).statusCode()).isEqualTo(200);
    }

    @Test
    void expiredUnknownAndMalformedResetTokensAreRejectedUniformly() throws Exception {
        Browser browser = registeredBrowser();
        String token = requestReset(browser, "alice@example.com");
        jdbc.update("UPDATE password_resets SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        HttpResponse<String> expired = browser.post(resetPath(token), resetCredentials("new-customer-password"));
        HttpResponse<String> unknown = browser.post(resetPath("0".repeat(64)), resetCredentials("new-customer-password"));
        HttpResponse<String> malformed = browser.post(resetPath("not-a-token"), resetCredentials("new-customer-password"));
        assertThat(expired.statusCode()).isEqualTo(400);
        assertThat(unknown.statusCode()).isEqualTo(400);
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(expired.body()).isEqualTo(unknown.body()).isEqualTo(malformed.body());
        assertThat(browser.get(resetPath(token)).statusCode()).isEqualTo(400);
        assertThat(encoder.matches("correct-password", jdbc.queryForObject("SELECT password_hash FROM users", String.class))).isTrue();
    }

    @Test
    void resetValidationAndCsrfFailuresDoNotConsumeToken() throws Exception {
        Browser browser = registeredBrowser();
        assertThat(browser.post("/api/auth/forgot-password", Map.of("email", "invalid-email")).statusCode()).isEqualTo(400);
        assertThat(browser.sendPost("/api/auth/forgot-password", Map.of("email", "alice@example.com"), null).statusCode()).isEqualTo(403);
        String token = requestReset(browser, "alice@example.com");
        assertThat(browser.sendPost(resetPath(token), resetCredentials("new-customer-password"), null).statusCode()).isEqualTo(403);
        assertThat(browser.post(resetPath(token), Map.of("newPassword", "new-customer-password", "confirmPassword", "different-password"))
            .statusCode()).isEqualTo(400);
        assertThat(browser.post(resetPath(token), resetCredentials("short")).statusCode()).isEqualTo(400);
        assertThat(browser.post(resetPath(token), resetCredentials("\u00e9".repeat(37))).statusCode()).isEqualTo(400);
        assertThat(browser.post(resetPath(token), Map.of("newPassword", "new-customer-password")).statusCode()).isEqualTo(400);
        assertThat(browser.get(resetPath(token)).statusCode()).isEqualTo(200);
        assertThat(browser.post(resetPath(token), resetCredentials("new-customer-password")).statusCode()).isEqualTo(200);
    }

    @Test
    void passwordResetRevokesAllTargetSessionsAndRememberedTokensOnly() throws Exception {
        Browser first = registeredBrowser();
        Browser second = new Browser();
        Browser other = new Browser();
        assertThat(first.post("/api/auth/login", login(true)).statusCode()).isEqualTo(200);
        assertThat(second.post("/api/auth/login", login(true)).statusCode()).isEqualTo(200);
        assertThat(other.post("/api/auth/register", registration("other@example.com")).statusCode()).isEqualTo(201);
        assertThat(other.post("/api/auth/login", Map.of("email", "other@example.com", "password", "correct-password")).statusCode())
            .isEqualTo(200);
        String token = requestReset(first, "alice@example.com");
        assertThat(first.post(resetPath(token), resetCredentials("new-customer-password")).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persistent_logins WHERE username = 'alice@example.com'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = 'alice@example.com'", Integer.class)).isZero();
        assertThat(first.get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(second.get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(other.get("/api/auth/me").statusCode()).isEqualTo(200);
    }

    @Test
    void adminPasswordCanBeRecoveredWithoutChangingRole() throws Exception {
        Browser admin = loggedInAdmin();
        Browser guest = new Browser();
        String token = requestReset(guest, "admin@example.com");
        assertThat(guest.post(resetPath(token), resetCredentials("new-admin-password")).statusCode()).isEqualTo(200);
        assertThat(admin.get("/api/admin/auth/me").statusCode()).isEqualTo(401);
        assertThat(guest.post("/api/admin/auth/login", adminLogin()).statusCode()).isEqualTo(401);
        assertThat(guest.post("/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "new-admin-password")).statusCode())
            .isEqualTo(200);
        assertThat(guest.get("/api/admin/auth/me").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT role FROM users", String.class)).isEqualTo("admin");
    }

    @Test
    void concurrentAttemptsCannotUseTheSameResetTokenTwice() throws Exception {
        Browser browser = registeredBrowser();
        String token = requestReset(browser, "alice@example.com");
        Browser first = new Browser();
        Browser second = new Browser();
        String firstCsrf = first.csrf();
        String secondCsrf = second.csrf();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent reset did not start");
                return first.sendPost(resetPath(token), resetCredentials("first-new-password"), firstCsrf).statusCode();
            });
            var two = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent reset did not start");
                return second.sendPost(resetPath(token), resetCredentials("second-new-password"), secondCsrf).statusCode();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int firstStatus = one.get(10, TimeUnit.SECONDS);
            int secondStatus = two.get(10, TimeUnit.SECONDS);
            assertThat(new int[] {firstStatus, secondStatus}).containsExactlyInAnyOrder(200, 400);
            String winningPassword = firstStatus == 200 ? "first-new-password" : "second-new-password";
            assertThat(encoder.matches(winningPassword, jdbc.queryForObject("SELECT password_hash FROM users", String.class))).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void deliveryFailureRollsBackTokenReplacementAndPreservesPreviousLink() throws Exception {
        Browser browser = registeredBrowser();
        String previous = requestReset(browser, "alice@example.com");
        String hash = jdbc.queryForObject("SELECT token_hash FROM password_resets", String.class);
        doThrow(new PasswordResetDeliveryException()).when(delivery).deliver(anyString(), anyString());
        HttpResponse<String> response = browser.post("/api/auth/forgot-password", Map.of("email", "alice@example.com"));
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(mapper.readTree(response.body()).get("code").asText()).isEqualTo("RESET_DELIVERY_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM password_resets", String.class)).isEqualTo(hash);
        assertThat(browser.get(resetPath(previous)).statusCode()).isEqualTo(200);
    }

    @Test
    void normalDeliveryDoesNotExposeLinksOrIdentifyRegisteredEmails() throws Exception {
        Browser browser = registeredBrowser();
        doReturn(null).when(delivery).deliver(anyString(), anyString());
        HttpResponse<String> known = browser.post("/api/auth/forgot-password", Map.of("email", "alice@example.com"));
        HttpResponse<String> unknown = browser.post("/api/auth/forgot-password", Map.of("email", "unknown@example.com"));
        assertThat(known.statusCode()).isEqualTo(200);
        assertThat(known.body()).isEqualTo(unknown.body());
        assertThat(mapper.readTree(known.body()).has("resetLink")).isFalse();
    }

    private String requestReset(Browser browser, String email) throws Exception {
        HttpResponse<String> response = browser.post("/api/auth/forgot-password", Map.of("email", email));
        assertThat(response.statusCode()).isEqualTo(200);
        String link = mapper.readTree(response.body()).get("resetLink").asText();
        return link.substring(link.lastIndexOf('/') + 1);
    }

    private String resetPath(String token) {
        return "/api/auth/reset-password/" + token;
    }

    private Map<String, Object> resetCredentials(String password) {
        return Map.of("newPassword", password, "confirmPassword", password);
    }

    @Test
    void adminLoginPersistsSessionAndAuthorizesAdminEndpoints() throws Exception {
        seedAdmin();
        Browser browser = new Browser();
        browser.csrf();
        String oldSession = browser.cookie("SESSION").getValue();
        HttpResponse<String> login = browser.post("/api/admin/auth/login", adminLogin());
        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode account = mapper.readTree(login.body());
        assertThat(account.get("role").asText()).isEqualTo("admin");
        assertThat(account.get("email").asText()).isEqualTo("admin@example.com");
        assertThat(account.has("passwordHash")).isFalse();
        assertThat(account.has("password")).isFalse();
        assertThat(browser.cookie("SESSION").getValue()).isNotEqualTo(oldSession);
        assertThat(browser.cookie("SESSION").getMaxAge()).isEqualTo(-1);
        assertThat(browser.get("/api/admin/auth/me").statusCode()).isEqualTo(200);
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?",
            Integer.class, "admin@example.com")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persistent_logins", Integer.class)).isZero();
    }

    @Test
    void guestCustomerAndRememberedCustomerCannotAccessAdminEndpoints() throws Exception {
        Browser guest = new Browser();
        assertThat(guest.get("/api/admin/auth/me").statusCode()).isEqualTo(401);
        assertThat(guest.post("/api/admin/auth/register", adminRegistration("second@example.com")).statusCode()).isEqualTo(401);
        Browser customer = registeredBrowser();
        assertThat(customer.post("/api/auth/login", login(true)).statusCode()).isEqualTo(200);
        assertThat(customer.get("/api/admin/auth/me").statusCode()).isEqualTo(403);
        assertThat(customer.post("/api/admin/auth/register", adminRegistration("second@example.com")).statusCode()).isEqualTo(403);
        customer.removeCookie("SESSION");
        // Spring Security prompts remembered users to reauthenticate on access denial.
        assertThat(customer.get("/api/admin/auth/me").statusCode()).isEqualTo(401);
        assertThat(customer.get("/api/auth/me").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'admin'", Integer.class)).isZero();
    }

    @Test
    void adminLoginRejectsCustomerWrongPasswordAndUnknownEmail() throws Exception {
        seedAdmin();
        Browser browser = registeredBrowser();
        HttpResponse<String> customer = browser.post("/api/admin/auth/login",
            Map.of("email", "alice@example.com", "password", "correct-password"));
        HttpResponse<String> wrong = browser.post("/api/admin/auth/login",
            Map.of("email", "admin@example.com", "password", "wrong-password"));
        HttpResponse<String> unknown = browser.post("/api/admin/auth/login",
            Map.of("email", "unknown@example.com", "password", "wrong-password"));
        assertThat(customer.statusCode()).isEqualTo(401);
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(customer.body()).isEqualTo(wrong.body()).isEqualTo(unknown.body());
        assertThat(browser.get("/api/admin/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void existingAdminCanCreateAnotherAdminWithoutChangingCurrentIdentity() throws Exception {
        Browser browser = loggedInAdmin();
        HttpResponse<String> created = browser.post("/api/admin/auth/register", adminRegistration("SECOND@Example.com"));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode account = mapper.readTree(created.body());
        assertThat(account.get("role").asText()).isEqualTo("admin");
        assertThat(account.get("email").asText()).isEqualTo("second@example.com");
        assertThat(account.has("passwordHash")).isFalse();
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'second@example.com'", String.class);
        assertThat(encoder.matches("second-admin-password", hash)).isTrue();
        assertThat(mapper.readTree(browser.get("/api/admin/auth/me").body()).get("email").asText()).isEqualTo("admin@example.com");

        Browser second = new Browser();
        assertThat(second.post("/api/admin/auth/login", Map.of("email", "second@example.com", "password", "second-admin-password"))
            .statusCode()).isEqualTo(200);
        assertThat(second.get("/api/admin/auth/me").statusCode()).isEqualTo(200);
    }

    @Test
    void adminRegistrationValidatesConfirmationAndRejectsDuplicateAccounts() throws Exception {
        Browser browser = loggedInAdmin();
        assertThat(browser.post("/api/admin/auth/register", Map.of("username", "Second", "email", "second@example.com",
            "password", "second-admin-password", "confirmPassword", "different-password")).statusCode()).isEqualTo(400);
        assertThat(browser.post("/api/admin/auth/register", Map.of("username", "Second", "email", "second@example.com",
            "password", "short", "confirmPassword", "short")).statusCode()).isEqualTo(400);
        assertThat(browser.post("/api/admin/auth/register", Map.of("username", "Second", "email", "second@example.com",
            "password", "\u00e9".repeat(37), "confirmPassword", "\u00e9".repeat(37))).statusCode()).isEqualTo(400);
        assertThat(browser.post("/api/admin/auth/register", adminRegistration("ADMIN@Example.com")).statusCode()).isEqualTo(409);
        Browser customer = registeredBrowser();
        assertThat(browser.post("/api/admin/auth/register", adminRegistration("alice@example.com")).statusCode()).isEqualTo(409);
        assertThat(customer.post("/api/auth/register", registration("ADMIN@Example.com")).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'admin'", Integer.class)).isEqualTo(1);
    }

    @Test
    void adminLoginAndCreationRequireCsrfAndLogoutInvalidatesAdminSession() throws Exception {
        seedAdmin();
        Browser browser = new Browser();
        assertThat(browser.sendPost("/api/admin/auth/login", adminLogin(), null).statusCode()).isEqualTo(403);
        String oldCsrf = browser.csrf();
        assertThat(browser.sendPost("/api/admin/auth/login", adminLogin(), oldCsrf).statusCode()).isEqualTo(200);
        assertThat(browser.sendPost("/api/admin/auth/register", adminRegistration("second@example.com"), oldCsrf).statusCode())
            .isEqualTo(403);
        assertThat(browser.sendPost("/api/admin/auth/register", adminRegistration("second@example.com"), null).statusCode())
            .isEqualTo(403);
        assertThat(browser.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
        assertThat(browser.get("/api/admin/auth/me").statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = 'admin@example.com'", Integer.class))
            .isZero();
    }

    @Test
    void switchingToAdminClearsCustomerRememberedLogin() throws Exception {
        seedAdmin();
        Browser browser = registeredBrowser();
        assertThat(browser.post("/api/auth/login", login(true)).statusCode()).isEqualTo(200);
        assertThat(browser.post("/api/admin/auth/login", adminLogin()).statusCode()).isEqualTo(200);
        assertThat(browser.get("/api/admin/auth/me").statusCode()).isEqualTo(200);
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persistent_logins", Integer.class)).isZero();
        browser.removeCookie("SESSION");
        assertThat(browser.get("/api/admin/auth/me").statusCode()).isEqualTo(401);
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void adminCreationIsAlsoProtectedAtServiceLayer() {
        assertThatThrownBy(() -> admins.register(new AdminRegisterRequest("Second", "second@example.com",
            "second-admin-password", "second-admin-password", null)))
            .isInstanceOfAny(AccessDeniedException.class, AuthenticationCredentialsNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void bootstrapCreatesFirstAdminOnceWithoutResettingPassword() throws Exception {
        bootstrap.run(null);
        String originalHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'bootstrap@example.com'", String.class);
        assertThat(encoder.matches("bootstrap-password", originalHash)).isTrue();
        jdbc.update("UPDATE users SET password_hash = ? WHERE email = 'bootstrap@example.com'", encoder.encode("changed-password"));
        bootstrap.run(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
        Browser browser = new Browser();
        assertThat(browser.post("/api/admin/auth/login", Map.of("email", "bootstrap@example.com", "password", "changed-password"))
            .statusCode()).isEqualTo(200);
    }

    @Test
    void bootstrapCannotPromoteAnExistingCustomer() throws Exception {
        Browser browser = new Browser();
        assertThat(browser.post("/api/auth/register", registration("bootstrap@example.com")).statusCode()).isEqualTo(201);
        assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("belongs to a customer");
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = 'bootstrap@example.com'", String.class)).isEqualTo("customer");
    }

    private void seedAdmin() {
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, 'admin')",
            "Admin", "admin@example.com", encoder.encode("correct-password"));
    }

    private Browser loggedInAdmin() throws Exception {
        seedAdmin();
        Browser browser = new Browser();
        assertThat(browser.post("/api/admin/auth/login", adminLogin()).statusCode()).isEqualTo(200);
        return browser;
    }

    private Map<String, Object> adminLogin() {
        return Map.of("email", "ADMIN@Example.com", "password", "correct-password");
    }

    private Map<String, Object> adminRegistration(String email) {
        return Map.of("username", "Second Admin", "email", email, "password", "second-admin-password",
            "confirmPassword", "second-admin-password", "phone", "0812345678");
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndDoesNotLogIn() throws Exception {
        Browser browser = new Browser();
        HttpResponse<String> response = browser.post("/api/auth/register", registration("Alice@Example.com"));
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode customer = mapper.readTree(response.body());
        assertThat(customer.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(customer.get("role").asText()).isEqualTo("customer");
        assertThat(customer.has("passwordHash")).isFalse();
        assertThat(customer.has("password")).isFalse();
        String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(hash).startsWith("$2a$10$");
        assertThat(encoder.matches("correct-password", hash)).isTrue();
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void duplicateEmailIsCaseInsensitiveAndReturnsConflict() throws Exception {
        Browser browser = new Browser();
        assertThat(browser.post("/api/auth/register", registration("alice@example.com")).statusCode()).isEqualTo(201);
        HttpResponse<String> duplicate = browser.post("/api/auth/register", registration("ALICE@example.com"));
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(mapper.readTree(duplicate.body()).get("code").asText()).isEqualTo("EMAIL_IN_USE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidFieldsUnicodePasswordAndRoleInjectionAreRejected() throws Exception {
        Browser browser = new Browser();
        assertThat(browser.post("/api/auth/register", Map.of("username", " ", "email", "bad", "password", "short")).statusCode())
            .isEqualTo(400);
        assertThat(browser.post("/api/auth/register", Map.of("username", "Alice", "email", "alice@example.com",
            "password", "\u00e9".repeat(37))).statusCode()).isEqualTo(400);
        assertThat(browser.post("/api/auth/register", Map.of("username", "Alice", "email", "alice@example.com",
            "password", "correct-password", "role", "admin")).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void loginPersistsSessionRotatesItsIdAndUsesSessionCookieByDefault() throws Exception {
        Browser browser = registeredBrowser();
        String oldSession = browser.cookie("SESSION").getValue();
        HttpResponse<String> login = browser.post("/api/auth/login", login(false));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(browser.cookie("SESSION").getValue()).isNotEqualTo(oldSession);
        assertThat(browser.cookie("SESSION").getMaxAge()).isEqualTo(-1);
        assertThat(login.headers().allValues("set-cookie")).anyMatch(cookie -> cookie.contains("HttpOnly"));
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?",
            Integer.class, "alice@example.com")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persistent_logins", Integer.class)).isZero();
    }

    @Test
    void wrongPasswordUnknownEmailAndAdminAccountCannotAuthenticate() throws Exception {
        Browser browser = registeredBrowser();
        HttpResponse<String> wrong = browser.post("/api/auth/login",
            Map.of("email", "alice@example.com", "password", "wrong-password"));
        HttpResponse<String> unknown = browser.post("/api/auth/login",
            Map.of("email", "unknown@example.com", "password", "wrong-password"));
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(wrong.body()).isEqualTo(unknown.body());
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, 'admin')",
            "Admin", "admin@example.com", encoder.encode("correct-password"));
        assertThat(browser.post("/api/auth/login", Map.of("email", "admin@example.com", "password", "correct-password"))
            .statusCode()).isEqualTo(401);
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void rememberMeRestoresLoginWithoutSessionAndLogoutRevokesIt() throws Exception {
        Browser browser = registeredBrowser();
        HttpResponse<String> login = browser.post("/api/auth/login", login(true));
        assertThat(login.statusCode()).isEqualTo(200);
        HttpCookie remembered = browser.cookie("remember-me");
        assertThat(remembered.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
        assertThat(login.headers().allValues("set-cookie")).anyMatch(cookie ->
            cookie.startsWith("remember-me=") && cookie.contains("HttpOnly"));
        String oldToken = jdbc.queryForObject("SELECT token FROM persistent_logins", String.class);
        browser.removeCookie("SESSION");
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT token FROM persistent_logins", String.class)).isNotEqualTo(oldToken);
        String restoredSessionId = new String(Base64.getDecoder().decode(browser.cookie("SESSION").getValue()),
            StandardCharsets.UTF_8);
        assertThat(browser.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persistent_logins", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE session_id = ?",
            Integer.class, restoredSessionId)).isZero();
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void expiredAndTamperedRememberMeCookiesCannotAuthenticate() throws Exception {
        Browser browser = registeredBrowser();
        assertThat(browser.post("/api/auth/login", login(true)).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE persistent_logins SET last_used = CURRENT_TIMESTAMP - INTERVAL '31 days'");
        browser.removeCookie("SESSION");
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);

        assertThat(browser.post("/api/auth/login", login(true)).statusCode()).isEqualTo(200);
        browser.cookie("remember-me").setValue("invalid-token");
        browser.removeCookie("SESSION");
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void loginWithoutRememberMeRevokesPriorRememberedLogins() throws Exception {
        Browser browser = registeredBrowser();
        assertThat(browser.post("/api/auth/login", login(true)).statusCode()).isEqualTo(200);
        assertThat(browser.post("/api/auth/login", login(false)).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persistent_logins", Integer.class)).isZero();
        browser.removeCookie("SESSION");
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void postEndpointsRequireCsrfAndLoginInvalidatesOldCsrfToken() throws Exception {
        Browser browser = registeredBrowser();
        assertThat(browser.sendPost("/api/auth/login", login(false), null).statusCode()).isEqualTo(403);
        String oldCsrf = browser.csrf();
        assertThat(browser.sendPost("/api/auth/login", login(false), oldCsrf).statusCode()).isEqualTo(200);
        assertThat(browser.sendPost("/api/auth/logout", Map.of(), oldCsrf).statusCode()).isEqualTo(403);
        assertThat(browser.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
    }

    @Test
    void corsAllowsConfiguredFrontendAndRejectsOtherOrigins() throws Exception {
        Browser browser = new Browser();
        HttpRequest allowed = HttpRequest.newBuilder(uri("/api/auth/login"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "POST").build();
        HttpResponse<String> response = browser.http.send(allowed, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
        HttpRequest rejected = HttpRequest.newBuilder(uri("/api/auth/login"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).header("Origin", "https://untrusted.example")
            .header("Access-Control-Request-Method", "POST").build();
        assertThat(browser.http.send(rejected, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    private Map<String, Object> registration(String email) {
        return Map.of("username", "Alice", "email", email, "password", "correct-password", "phone", "0812345678");
    }

    private Map<String, Object> login(boolean rememberMe) {
        return Map.of("email", "ALICE@example.com", "password", "correct-password", "rememberMe", rememberMe);
    }

    private Browser registeredBrowser() throws Exception {
        Browser browser = new Browser();
        assertThat(browser.post("/api/auth/register", registration("alice@example.com")).statusCode()).isEqualTo(201);
        return browser;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        String csrf() throws Exception {
            HttpResponse<String> response = get("/api/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            return mapper.readTree(response.body()).get("token").asText();
        }

        HttpResponse<String> post(String path, Object body) throws Exception {
            return sendPost(path, body, csrf());
        }

        HttpResponse<String> sendPost(String path, Object body, String token) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpCookie cookie(String name) {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals(name))
                .findFirst().orElseThrow();
        }

        void removeCookie(String name) {
            HttpCookie cookie = cookie(name);
            cookies.getCookieStore().remove(uri("/"), cookie);
        }
    }
}
