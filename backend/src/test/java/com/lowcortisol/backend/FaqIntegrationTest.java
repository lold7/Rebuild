package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.faq.FaqDemoData;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
class FaqIntegrationTest {
    private static final String API = "/api/faqs";
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "faq-test-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> false);
        registry.add("app.faq.demo-enabled", () -> true);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired FaqDemoData demo;
    @Autowired PasswordEncoder encoder;
    Browser guest;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM faqs");
        demo.run(null);
        guest = new Browser();
    }

    @Test
    void listAndDetailsArePublicArraysWithTheFourFaqFields() throws Exception {
        JsonNode list = ok(guest.get(API));
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isEqualTo(5);
        assertThat(list.get(0).get("question").asText()).isEqualTo("How do I track my order?");
        assertThat(list.get(4).get("sortOrder").asInt()).isEqualTo(5);
        for (JsonNode faq : list) {
            assertThat(faq.size()).isEqualTo(4);
            assertThat(faq.get("question").asText()).isNotBlank();
            assertThat(faq.get("answer").asText()).isNotBlank();
            assertThat(ok(guest.get(API + "/" + faq.get("faqId").asLong()))).isEqualTo(faq);
            assertThat(faq.has("isVisible")).isFalse();
            assertThat(faq.has("createdAt")).isFalse();
        }
    }

    @Test
    void noRowsOrOnlyHiddenRowsReturnAnEmptyArrayAndMissingDetails() throws Exception {
        long id = ok(guest.get(API)).get(0).get("faqId").asLong();
        jdbc.update("UPDATE faqs SET is_visible = FALSE");
        assertThat(ok(guest.get(API)).isEmpty()).isTrue();
        JsonNode hidden = error(guest.get(API + "/" + id), 404, "FAQ_NOT_FOUND");
        assertThat(error(guest.get(API + "/999999"), 404, "FAQ_NOT_FOUND")).isEqualTo(hidden);
        assertThat(ok(guest.get(search("tracking"))).isEmpty()).isTrue();
        jdbc.update("DELETE FROM faqs");
        assertThat(ok(guest.get(API)).isEmpty()).isTrue();
    }

    @Test
    void sortingUsesDisplayOrderThenIdAndReflectsCommittedEdits() throws Exception {
        long first = insert("First", "First answer", 0, true);
        long tie = insert("Tie", "Second answer", 0, true);
        insert("Draft", "Private draft", 0, false);
        JsonNode list = ok(guest.get(API));
        assertThat(ids(list)).startsWith(first, tie);
        jdbc.update("UPDATE faqs SET question = 'Updated question', answer = 'Updated answer', sort_order = 99 WHERE faq_id = ?", first);
        JsonNode updated = ok(guest.get(API + "/" + first));
        assertThat(updated.get("answer").asText()).isEqualTo("Updated answer");
        assertThat(updated.get("sortOrder").asInt()).isEqualTo(99);
        assertThat(ids(ok(guest.get(API)))).endsWith(first);
        jdbc.update("DELETE FROM faqs WHERE faq_id = ?", tie);
        error(guest.get(API + "/" + tie), 404, "FAQ_NOT_FOUND");
    }

    @Test
    void searchMatchesQuestionsAndAnswersWithoutExposingHiddenContent() throws Exception {
        long id = insert("หนังสือใหม่", "Café book help", 0, true);
        insert("Private question", "Private answer token", 0, false);
        assertThat(ok(guest.get(search(" TRACKING NUMBER "))).size()).isEqualTo(1);
        assertThat(ok(guest.get(search("return policy"))).size()).isEqualTo(1);
        assertThat(ids(ok(guest.get(search("หนังสือ"))))).containsExactly(id);
        assertThat(ids(ok(guest.get(search("Café"))))).containsExactly(id);
        assertThat(ok(guest.get(search("Private answer token"))).isEmpty()).isTrue();
        assertThat(ok(guest.get(search("unmatched"))).isEmpty()).isTrue();
        assertThat(ok(guest.get(search("   ")))).isEqualTo(ok(guest.get(API)));
    }

    @Test
    void wildcardsAndSqlInjectionAreLiteralSearchText() throws Exception {
        long id = insert("100%_!", "An exact special phrase", 0, true);
        for (String query : List.of("%", "_", "!", "%_!")) assertThat(ids(ok(guest.get(search(query))))).containsExactly(id);
        assertThat(ok(guest.get(search("' OR 1=1 --"))).isEmpty()).isTrue();
        assertThat(ok(guest.get(API)).size()).isEqualTo(6);
    }

    @Test
    void malformedIdsAndOverlongSearchReturnStructuredErrors() throws Exception {
        for (String id : List.of("0", "-1", "abc", "1.5", "9223372036854775808"))
            error(guest.get(API + "/" + id), 400, "INVALID_FAQ_REQUEST");
        error(guest.get(search("x".repeat(256))), 400, "INVALID_FAQ_REQUEST");
        assertThat(guest.get(search("x".repeat(255))).statusCode()).isEqualTo(200);
    }

    @Test
    void customersAndAdminsCanReadTheSamePublicContent() throws Exception {
        Browser customer = new Browser(), admin = new Browser();
        assertThat(customer.send("POST", "/api/auth/register", Map.of("username", "Alice", "email", "alice@example.com", "password", "correct-password"), true).statusCode()).isEqualTo(201);
        ok(customer.send("POST", "/api/auth/login", Map.of("email", "alice@example.com", "password", "correct-password"), true));
        jdbc.update("INSERT INTO users (username, email, password_hash, role) VALUES ('Admin', 'admin@example.com', ?, 'admin')", encoder.encode("correct-password"));
        ok(admin.send("POST", "/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "correct-password"), true));
        JsonNode expected = ok(guest.get(API));
        assertThat(ok(customer.get(API))).isEqualTo(expected);
        assertThat(ok(admin.get(API))).isEqualTo(expected);
        long id = expected.get(0).get("faqId").asLong();
        assertThat(ok(customer.get(API + "/" + id))).isEqualTo(expected.get(0));
        assertThat(ok(admin.get(API + "/" + id))).isEqualTo(expected.get(0));
    }

    @Test
    void publicGetDoesNotPermitAnonymousWritesAndCsrfStillApplies() throws Exception {
        JsonNode before = ok(guest.get(API));
        long id = before.get(0).get("faqId").asLong();
        for (String path : List.of(API, API + "/" + id)) {
            for (String method : List.of("POST", "PUT", "DELETE")) {
                assertThat(guest.send(method, path, Map.of("question", "Changed"), false).statusCode()).isEqualTo(403);
                assertThat(guest.send(method, path, Map.of("question", "Changed"), true).statusCode()).isEqualTo(401);
            }
        }
        assertThat(ok(guest.get(API))).isEqualTo(before);
    }

    @Test
    void allowedBrowserOriginsCanFetchPublicFaqsAndUnlistedOriginsCannot() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + API))
            .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> preflight = guest.http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(preflight.statusCode()).isEqualTo(200);
        assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:5173");
        assertThat(preflight.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
        var allowed = HttpRequest.newBuilder(URI.create("http://localhost:" + port + API)).header("Origin", "http://localhost:5173").GET().build();
        assertThat(guest.http.send(allowed, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        var denied = HttpRequest.newBuilder(URI.create("http://localhost:" + port + API)).header("Origin", "https://unlisted.example").GET().build();
        assertThat(guest.http.send(denied, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void databaseRejectsBlankOversizedOrNegativeContent() {
        for (var values : List.of(List.of(" ", "Answer", 0), List.of("Question", " ", 0),
            List.of("x".repeat(501), "Answer", 0), List.of("Question", "x".repeat(10001), 0), List.of("Question", "Answer", -1))) {
            Object[] parameters = values.toArray();
            assertThatThrownBy(() -> jdbc.update("INSERT INTO faqs (question, answer, sort_order) VALUES (?, ?, ?)", parameters))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void repeatedDemoImportsPreserveExistingAndHiddenContent() throws Exception {
        JsonNode before = ok(guest.get(API)); demo.run(null);
        assertThat(ok(guest.get(API))).isEqualTo(before);
        jdbc.update("DELETE FROM faqs");
        long custom = insert("Custom hidden FAQ", "Custom answer", 10, false);
        demo.run(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM faqs", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT question FROM faqs WHERE faq_id = ?", String.class, custom)).isEqualTo("Custom hidden FAQ");
    }

    @Test
    void concurrentDemoImportsSeedAnEmptyTableOnce() throws Exception {
        jdbc.update("DELETE FROM faqs");
        var executor = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); demo.run(null); return true; });
            var second = executor.submit(() -> { start.await(); demo.run(null); return true; });
            start.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
            assertThat(ok(guest.get(API)).size()).isEqualTo(5);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void failedDemoImportRollsBackAllRowsAndCanBeRetried() throws Exception {
        jdbc.update("DELETE FROM faqs");
        jdbc.execute("CREATE FUNCTION reject_faq_sample() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.sort_order = 3 THEN RAISE EXCEPTION 'sample failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_faq_sample BEFORE INSERT ON faqs FOR EACH ROW EXECUTE FUNCTION reject_faq_sample()");
        try {
            assertThatThrownBy(() -> demo.run(null)).isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM faqs", Long.class)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_faq_sample ON faqs"); jdbc.execute("DROP FUNCTION reject_faq_sample()");
        }
        demo.run(null); assertThat(ok(guest.get(API)).size()).isEqualTo(5);
    }

    private long insert(String question, String answer, int order, boolean visible) {
        return jdbc.queryForObject("INSERT INTO faqs (question, answer, sort_order, is_visible) VALUES (?, ?, ?, ?) RETURNING faq_id",
            Long.class, question, answer, order, visible);
    }
    private String search(String value) { return API + "?q=" + URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private List<Long> ids(JsonNode items) {
        List<Long> result = new ArrayList<>(); items.forEach(item -> result.add(item.get("faqId").asLong())); return result;
    }
    private JsonNode error(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        JsonNode result = json.readTree(response.body()); assertThat(result.get("code").asText()).isEqualTo(code); return result;
    }
    private JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200); return json.readTree(response.body());
    }
    private class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> send(String method, String path, Object body, boolean csrf) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (csrf) request.header("X-CSRF-TOKEN", ok(get("/api/auth/csrf")).get("token").asText());
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
