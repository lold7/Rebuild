package com.lowcortisol.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.adminproduct.AdminProductService;
import com.lowcortisol.backend.adminproduct.ProductWriteRequest;
import com.lowcortisol.backend.adminproduct.StockUpdateRequest;
import com.lowcortisol.backend.catalog.CatalogDemoData;
import jakarta.validation.ConstraintViolationException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
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
class AdminProductIntegrationTest {
    private static Path imageDirectory;
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        imageDirectory = Files.createTempDirectory("admin-product-images-");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.remember-me-key", () -> "admin-product-secret-at-least-32-characters");
        registry.add("app.catalog.demo-enabled", () -> true);
        registry.add("app.bootstrap.admin.enabled", () -> false);
        registry.add("app.password-reset.delivery", () -> "disabled");
        registry.add("app.catalog.image-directory", () -> imageDirectory.toString());
    }

    @AfterAll
    static void cleanupImages() throws Exception {
        if (imageDirectory != null) {
            try (var paths = Files.walk(imageDirectory)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired CatalogDemoData demo;
    @Autowired PasswordEncoder encoder;
    @Autowired AdminProductService products;

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
    void adminCanSeeHiddenProductsAndCategoriesWithPaging() throws Exception {
        Browser admin = admin();
        jdbc.update("UPDATE products SET is_visible = FALSE WHERE product_id = 1");
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 2");
        JsonNode page = ok(admin.get("/api/admin/products"));
        assertThat(page.get("totalItems").asInt()).isEqualTo(60);
        assertThat(page.get("pageSize").asInt()).isEqualTo(15);
        assertThat(page.get("totalPages").asInt()).isEqualTo(4);
        assertThat(page.get("items").size()).isEqualTo(15);
        assertThat(ok(admin.get("/api/admin/products/1")).get("isVisible").asBoolean()).isFalse();
        assertThat(ok(admin.get("/api/admin/products/14")).get("categoryVisible").asBoolean()).isFalse();
        assertThat(ok(admin.get("/api/admin/products?visibility=hidden")).get("totalItems").asInt()).isEqualTo(13);
        assertThat(ok(admin.get("/api/admin/products?visibility=visible")).get("totalItems").asInt()).isEqualTo(47);
        JsonNode categories = ok(admin.get("/api/admin/categories"));
        assertThat(categories.size()).isEqualTo(5);
        assertThat(categories.get(1).get("isVisible").asBoolean()).isFalse();
        assertThat(categories.get(1).get("productCount").asInt()).isEqualTo(12);
    }

    @Test
    void inventoryFiltersAndSummaryUseAuthoritativeQuantityIncludingHiddenItems() throws Exception {
        Browser admin = admin();
        jdbc.update("UPDATE products SET product_quantity = 10");
        jdbc.update("UPDATE products SET product_quantity = 0, is_visible = FALSE WHERE product_id = 1");
        jdbc.update("UPDATE products SET product_quantity = 3 WHERE product_id = 2");
        jdbc.update("UPDATE products SET product_quantity = 5 WHERE product_id = 3");
        jdbc.update("UPDATE products SET product_quantity = 6 WHERE product_id = 4");
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 2");
        JsonNode summary = ok(admin.get("/api/admin/inventory/summary"));
        assertThat(summary.get("totalProducts").asInt()).isEqualTo(60);
        assertThat(summary.get("totalUnits").asLong()).isEqualTo(574);
        assertThat(summary.get("outOfStockProducts").asInt()).isEqualTo(1);
        assertThat(summary.get("lowStockProducts").asInt()).isEqualTo(2);
        assertThat(summary.get("hiddenProducts").asInt()).isEqualTo(13);
        assertThat(ok(admin.get("/api/admin/inventory?stock=out")).get("items").get(0).get("product").get("productId").asInt()).isEqualTo(1);
        assertThat(ok(admin.get("/api/admin/inventory?stock=low&lowStockThreshold=3")).get("totalItems").asInt()).isEqualTo(1);
        assertThat(ok(admin.get("/api/admin/inventory?stock=low&lowStockThreshold=0")).get("totalItems").asInt()).isZero();
        assertThat(ok(admin.get("/api/admin/products?categoryId=2")).get("totalItems").asInt()).isEqualTo(12);
    }

    @Test
    void createNormalizesMetadataAndRecordsInitialStockForTheAuthenticatedAdmin() throws Exception {
        Browser admin = admin();
        Map<String, Object> request = details();
        request.put("productName", " New book ");
        request.put("productQuantity", 7);
        request.put("productAttributes", Map.of("author", " New Author ", "sku", " BOOK-1 ", "format", List.of(" Paperback ", "Hardcover"), "discount", 10));
        HttpResponse<String> response = admin.send("POST", "/api/admin/products", request);
        JsonNode result = created(response);
        long id = result.get("product").get("productId").asLong();
        assertThat(id).isGreaterThan(60);
        assertThat(result.get("version").asLong()).isZero();
        assertThat(result.get("isVisible").asBoolean()).isTrue();
        assertThat(result.get("product").get("productName").asText()).isEqualTo("New book");
        assertThat(result.get("product").get("productQuantity").asInt()).isEqualTo(7);
        assertThat(result.get("product").get("productAttributes").get("sku").asText()).isEqualTo("BOOK-1");
        assertThat(result.get("product").get("productAttributes").get("format").get(0).asText()).isEqualTo("Paperback");
        assertThat(ok(admin.get(response.headers().firstValue("Location").orElseThrow()))).isEqualTo(result);
        JsonNode adjustment = ok(admin.get("/api/admin/products/" + id + "/stock-history")).get("items").get(0);
        assertThat(adjustment.get("quantityBefore").asInt()).isZero();
        assertThat(adjustment.get("quantityAfter").asInt()).isEqualTo(7);
        assertThat(adjustment.get("delta").asLong()).isEqualTo(7);
        assertThat(adjustment.get("adminUserId").asLong()).isEqualTo(admin.userId);
        assertThat(new Browser().get("/api/products/" + id).statusCode()).isEqualTo(200);
    }

    @Test
    void omittedCreateFieldsDefaultToEmptyMetadataAndZeroStock() throws Exception {
        Browser admin = admin();
        JsonNode result = created(admin.send("POST", "/api/admin/products", details()));
        JsonNode product = result.get("product");
        assertThat(product.get("productQuantity").asInt()).isZero();
        assertThat(product.get("productImages").size()).isZero();
        assertThat(product.get("productAttributes").size()).isZero();
        assertThat(product.get("inStock").asBoolean()).isFalse();
        Map<String, Object> hidden = details();
        hidden.put("categoryId", 2);
        jdbc.update("UPDATE categories SET is_visible = FALSE WHERE category_id = 2");
        long id = created(admin.send("POST", "/api/admin/products", hidden)).get("product").get("productId").asLong();
        assertThat(admin.get("/api/admin/products/" + id).statusCode()).isEqualTo(200);
        assertThat(new Browser().get("/api/products/" + id).statusCode()).isEqualTo(404);
    }

    @Test
    void metadataEditsPreserveOmittedImagesAttributesQuantityAndVisibility() throws Exception {
        Browser admin = admin();
        JsonNode original = ok(admin.get("/api/admin/products/1"));
        Map<String, Object> request = details();
        request.put("expectedVersion", 0);
        request.put("categoryId", 2);
        request.put("productName", "Edited title");
        JsonNode edited = ok(admin.send("PUT", "/api/admin/products/1", request));
        assertThat(edited.get("version").asLong()).isEqualTo(1);
        assertThat(edited.get("product").get("categoryId").asInt()).isEqualTo(2);
        assertThat(edited.get("product").get("productImages")).isEqualTo(original.get("product").get("productImages"));
        assertThat(edited.get("product").get("productAttributes")).isEqualTo(original.get("product").get("productAttributes"));
        assertThat(edited.get("product").get("productQuantity")).isEqualTo(original.get("product").get("productQuantity"));
        assertThat(ok(admin.get("/api/admin/products/1/stock-history")).get("totalItems").asInt()).isZero();
        request.put("expectedVersion", 1);
        request.put("productQuantity", 9);
        request.put("productImages", List.of());
        request.put("productAttributes", Map.of());
        request.put("isVisible", false);
        JsonNode replaced = ok(admin.send("PUT", "/api/admin/products/1", request));
        assertThat(replaced.get("product").get("productImages").size()).isZero();
        assertThat(replaced.get("product").get("productAttributes").size()).isZero();
        assertThat(replaced.get("isVisible").asBoolean()).isFalse();
        assertThat(ok(admin.get("/api/admin/products/1/stock-history")).get("items").get(0).get("reason").asText()).isEqualTo("Product edit");
    }

    @Test
    void duplicateSkusAreRejectedWithoutChangingProductsOrInventoryHistory() throws Exception {
        Browser admin = admin();
        Map<String, Object> request = details();
        request.put("productAttributes", Map.of("sku", "BOOK-ONE"));
        created(admin.send("POST", "/api/admin/products", request));
        request.put("productAttributes", Map.of("sku", " book-one "));
        assertError(admin.send("POST", "/api/admin/products", request), 409, "SKU_IN_USE");
        request.put("expectedVersion", 0);
        request.put("productQuantity", 100);
        assertError(admin.send("PUT", "/api/admin/products/1", request), 409, "SKU_IN_USE");
        assertThat(stock(1)).isEqualTo(74);
        assertThat(version(1)).isZero();
        assertThat(ok(admin.get("/api/admin/products/1/stock-history")).get("totalItems").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(61);
    }

    @Test
    void stockUpdatesAreAtomicAuditedAndNoOpUpdatesDoNotAdvanceTheVersion() throws Exception {
        Browser admin = admin();
        ok(admin.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", 74, "expectedVersion", 0, "reason", "No change")));
        assertThat(version(1)).isZero();
        assertThat(ok(admin.get("/api/admin/products/1/stock-history")).get("totalItems").asInt()).isZero();
        JsonNode updated = ok(admin.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", 0, "expectedVersion", 0, "reason", " Damaged copies ")));
        assertThat(updated.get("version").asLong()).isEqualTo(1);
        assertThat(updated.get("product").get("inStock").asBoolean()).isFalse();
        JsonNode log = ok(admin.get("/api/admin/products/1/stock-history")).get("items").get(0);
        assertThat(log.get("delta").asLong()).isEqualTo(-74);
        assertThat(log.get("reason").asText()).isEqualTo("Damaged copies");
        assertThat(log.get("adminUserId").asLong()).isEqualTo(admin.userId);
        // Setting stock to zero hides the book; restocking shows it again.
        assertThat(updated.get("isVisible").asBoolean()).isFalse();
        assertThat(new Browser().get("/api/products/1").statusCode()).isEqualTo(404);
        JsonNode restocked = ok(admin.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", 5, "expectedVersion", 1, "reason", "Restock")));
        assertThat(restocked.get("isVisible").asBoolean()).isTrue();
        assertThat(new Browser().get("/api/products/1").statusCode()).isEqualTo(200);
    }

    @Test
    void manuallyHiddenProductsStayHiddenWhenStockChanges() throws Exception {
        Browser admin = admin();
        ok(admin.send("PUT", "/api/admin/products/1/visibility", Map.of("isVisible", false, "expectedVersion", 0)));
        JsonNode updated = ok(admin.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", 60, "expectedVersion", 1, "reason", "Count")));
        assertThat(updated.get("isVisible").asBoolean()).isFalse();
        Map<String, Object> created = new HashMap<>(Map.of("categoryId", 1, "productName", "No stock yet", "productPrice", 5, "productQuantity", 0));
        JsonNode product = created(admin.send("POST", "/api/admin/products", created));
        assertThat(product.get("isVisible").asBoolean()).isFalse();
    }

    @Test
    void inventoryHistoryIsBoundedAndNewestFirst() throws Exception {
        Browser admin = admin();
        for (int i = 0; i < 17; i++) {
            ok(admin.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", i, "expectedVersion", i, "reason", "Count " + i)));
        }
        JsonNode page = ok(admin.get("/api/admin/products/1/stock-history"));
        assertThat(page.get("items").size()).isEqualTo(15);
        assertThat(page.get("totalItems").asInt()).isEqualTo(17);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("items").get(0).get("reason").asText()).isEqualTo("Count 16");
        assertThat(ok(admin.get("/api/admin/products/1/stock-history?page=2")).get("items").size()).isEqualTo(2);
    }

    @Test
    void searchIncludesSkusAuthorsAndTreatsWildcardCharactersLiterally() throws Exception {
        Browser admin = admin();
        Map<String, Object> request = details();
        request.put("productAttributes", Map.of("sku", "SKU%_!", "author", "Unique Writer"));
        JsonNode product = created(admin.send("POST", "/api/admin/products", request));
        long id = product.get("product").get("productId").asLong();
        for (String keyword : List.of("SKU%_!", "Unique Writer")) {
            JsonNode page = ok(admin.get("/api/admin/products?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)));
            assertThat(page.get("totalItems").asInt()).isEqualTo(1);
            assertThat(page.get("items").get(0).get("product").get("productId").asLong()).isEqualTo(id);
        }
        assertThat(ok(admin.get("/api/admin/products?q=" + URLEncoder.encode("' OR 1=1--", StandardCharsets.UTF_8))).get("totalItems").asInt()).isZero();
    }

    @Test
    void visibilityChangesAffectPublicCatalogCartAndWishlist() throws Exception {
        Browser admin = admin();
        Browser customer = customer();
        customer.add(1, 1);
        ok(customer.send("POST", "/api/account/wishlist/add", Map.of("productId", 1)));
        JsonNode hidden = ok(admin.send("PUT", "/api/admin/products/1/visibility", Map.of("isVisible", false, "expectedVersion", 0)));
        assertThat(hidden.get("version").asLong()).isEqualTo(1);
        assertThat(new Browser().get("/api/products/1").statusCode()).isEqualTo(404);
        assertThat(ok(customer.get("/api/cart")).get("items").get(0).get("available").asBoolean()).isFalse();
        assertThat(ok(customer.get("/api/account/wishlist")).get("items").get(0).get("available").asBoolean()).isFalse();
        ok(admin.send("PUT", "/api/admin/products/1/visibility", Map.of("isVisible", true, "expectedVersion", 1)));
        assertThat(new Browser().get("/api/products/1").statusCode()).isEqualTo(200);
        assertThat(ok(customer.get("/api/cart")).get("valid").asBoolean()).isTrue();
    }

    @Test
    void checkoutStockDeductionInvalidatesStaleAdminEdits() throws Exception {
        Browser admin = admin();
        Browser customer = customer();
        customer.add(1, 2);
        created(customer.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER")));
        assertThat(version(1)).isEqualTo(1);
        Map<String, Object> stale = details();
        stale.put("expectedVersion", 0);
        stale.put("productQuantity", 74);
        assertError(admin.send("PUT", "/api/admin/products/1", stale), 409, "STALE_PRODUCT_VERSION");
        assertError(admin.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", 100, "expectedVersion", 0, "reason", "Restock")), 409, "STALE_PRODUCT_VERSION");
        assertThat(stock(1)).isEqualTo(72);
        ok(admin.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", 80, "expectedVersion", 1, "reason", "Restock")));
        JsonNode log = ok(admin.get("/api/admin/products/1/stock-history")).get("items").get(0);
        assertThat(log.get("quantityBefore").asInt()).isEqualTo(72);
        assertThat(log.get("quantityAfter").asInt()).isEqualTo(80);
    }

    @Test
    void productsInPastOrdersCannotBeDeletedButOthersClearCartAndWishlist() throws Exception {
        Browser admin = admin();
        Browser customer = customer();
        customer.add(1, 1);
        JsonNode order = created(customer.send("POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER")));
        assertError(admin.send("DELETE", "/api/admin/products/1?expectedVersion=0", Map.of()), 409, "STALE_PRODUCT_VERSION");
        // A book that appears in past orders must be hidden instead of deleted.
        assertError(admin.send("DELETE", "/api/admin/products/1?expectedVersion=1", Map.of()), 409, "PRODUCT_IN_ORDERS");
        assertThat(admin.get("/api/admin/products/1").statusCode()).isEqualTo(200);
        assertThat(ok(customer.get("/api/orders/" + order.get("orderId").asLong()))).isEqualTo(order);

        customer.add(2, 2);
        ok(customer.send("POST", "/api/account/wishlist/add", Map.of("productId", 2)));
        assertThat(admin.send("DELETE", "/api/admin/products/2?expectedVersion=0", Map.of()).statusCode()).isEqualTo(204);
        assertThat(admin.get("/api/admin/products/2").statusCode()).isEqualTo(404);
        assertThat(ok(customer.get("/api/cart")).get("totalItems").asInt()).isZero();
        assertThat(ok(customer.get("/api/account/wishlist")).get("items").size()).isZero();
        assertThat(ok(customer.get("/api/account/orders")).get("items").get(0)).isEqualTo(order);
    }

    @Test
    void concurrentAdminStockUpdatesRejectOneStaleVersion() throws Exception {
        Browser first = admin(), second = adminLogin();
        var responses = concurrent(first, "PUT", "/api/admin/products/1/stock", Map.of("quantity", 10, "expectedVersion", 0, "reason", "First"),
            second, "PUT", "/api/admin/products/1/stock", Map.of("quantity", 20, "expectedVersion", 0, "reason", "Second"));
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200, 409);
        assertThat(stock(1)).isIn(10, 20);
        assertThat(version(1)).isEqualTo(1);
        assertThat(ok(first.get("/api/admin/products/1/stock-history")).get("totalItems").asInt()).isEqualTo(1);
    }

    @Test
    void concurrentMetadataEditsCannotOverwriteEachOther() throws Exception {
        Browser first = admin(), second = adminLogin();
        Map<String, Object> one = details(), two = details();
        one.put("productName", "First title"); one.put("expectedVersion", 0);
        two.put("productName", "Second title"); two.put("expectedVersion", 0);
        var responses = concurrent(first, "PUT", "/api/admin/products/1", one, second, "PUT", "/api/admin/products/1", two);
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200, 409);
        assertThat(version(1)).isEqualTo(1);
        assertThat(ok(first.get("/api/admin/products/1")).get("product").get("productName").asText()).isIn("First title", "Second title");
    }

    @Test
    void concurrentCheckoutAndStockCorrectionPreserveTheWinningTransaction() throws Exception {
        Browser admin = admin(), customer = customer();
        customer.add(1, 2);
        var responses = concurrent(admin, "PUT", "/api/admin/products/1/stock", Map.of("quantity", 0, "expectedVersion", 0, "reason", "No copies"),
            customer, "POST", "/api/orders", Map.of("paymentMethod", "BANK_TRANSFER"));
        int adminStatus = responses.get(0).statusCode(), orderStatus = responses.get(1).statusCode();
        assertThat(version(1)).isEqualTo(1);
        if (orderStatus == 201) {
            assertThat(adminStatus).isEqualTo(409);
            assertThat(stock(1)).isEqualTo(72);
            assertThat(ok(customer.get("/api/cart")).get("totalItems").asInt()).isZero();
        } else {
            assertThat(orderStatus).isEqualTo(409);
            assertThat(adminStatus).isEqualTo(200);
            assertThat(stock(1)).isZero();
            assertThat(ok(customer.get("/api/cart")).get("totalQuantity").asInt()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        }
    }

    @Test
    void stockAndVersionRollBackIfInventoryHistoryCannotBeStored() throws Exception {
        Browser admin = admin();
        jdbc.execute("""
            CREATE FUNCTION reject_test_adjustment() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN RAISE EXCEPTION 'Simulated inventory storage failure'; END $$
            """);
        jdbc.execute("CREATE TRIGGER reject_test_adjustment BEFORE INSERT ON inventory_adjustments FOR EACH ROW EXECUTE FUNCTION reject_test_adjustment()");
        try {
            assertError(admin.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", 100, "expectedVersion", 0, "reason", "Restock")), 500, "DATABASE_ERROR");
            assertThat(stock(1)).isEqualTo(74);
            assertThat(version(1)).isZero();
            assertThat(ok(admin.get("/api/admin/products/1/stock-history")).get("totalItems").asInt()).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_adjustment ON inventory_adjustments");
            jdbc.execute("DROP FUNCTION reject_test_adjustment()");
        }
        ok(admin.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", 100, "expectedVersion", 0, "reason", "Restock")));
    }

    @Test
    void pngUploadsHaveGeneratedNamesAndRemainAvailableAfterProductDeletion() throws Exception {
        Browser admin = admin();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", buffer);
        JsonNode uploaded = created(admin.upload(buffer.toByteArray(), "image/jpeg", "../../unsafe.png"));
        assertThat(uploaded.get("image").asText()).matches("[a-f0-9-]{36}\\.png");
        HttpResponse<String> image = new Browser().get(uploaded.get("url").asText());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.headers().firstValue("Content-Type").orElseThrow()).isEqualTo("image/png");
        assertThat(image.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(image.headers().firstValue("Cache-Control").orElseThrow()).contains("immutable");
        Map<String, Object> request = details(); request.put("productImages", List.of(uploaded.get("image").asText()));
        long id = created(admin.send("POST", "/api/admin/products", request)).get("product").get("productId").asLong();
        assertThat(admin.send("DELETE", "/api/admin/products/" + id + "?expectedVersion=0", Map.of()).statusCode()).isEqualTo(204);
        assertThat(new Browser().get(uploaded.get("url").asText()).statusCode()).isEqualTo(200);
    }

    @Test
    void jpegUploadsAreSupportedAndInvalidImagesAndDimensionsAreRejected() throws Exception {
        Browser admin = admin();
        var jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg);
        JsonNode uploaded = created(admin.upload(jpeg.toByteArray(), "image/jpeg", "cover.jpg"));
        assertThat(uploaded.get("image").asText()).endsWith(".jpg");
        assertThat(new Browser().get(uploaded.get("url").asText()).headers().firstValue("Content-Type")).contains("image/jpeg");
        assertError(admin.upload("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8), "image/png", "cover.png"), 400, "INVALID_PRODUCT_IMAGE");
        var huge = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", huge);
        assertError(admin.upload(huge.toByteArray(), "image/png", "wide.png"), 400, "INVALID_PRODUCT_IMAGE");
        assertThat(new Browser().get("/api/product-images/secret.txt").statusCode()).isEqualTo(404);
        assertThat(new Browser().get("/api/product-images/00000000-0000-0000-0000-000000000000.png").statusCode()).isEqualTo(404);
    }

    @Test
    void uploadsLargerThanFiveMiBAreRejected() throws Exception {
        Browser admin = admin();
        assertError(admin.upload(new byte[5 * 1024 * 1024 + 1], "image/png", "huge.png"), 413, "PRODUCT_IMAGE_TOO_LARGE");
    }

    @Test
    void validationRejectsBadPricesStockJsonMetadataAndIdentityInjection() throws Exception {
        Browser admin = admin();
        assertThat(admin.send("POST", "/api/admin/products", Map.of()).statusCode()).isEqualTo(400);
        for (var field : List.of(Map.entry("productName", " "), Map.entry("productName", "x".repeat(256)),
            Map.entry("categoryId", 0), Map.entry("categoryId", 1.5), Map.entry("productPrice", -0.01),
            Map.entry("productPrice", 1.234), Map.entry("productPrice", 100000000),
            Map.entry("productQuantity", -1), Map.entry("productQuantity", 1.5), Map.entry("productQuantity", 2147483648L),
            Map.entry("productImages", List.of("../file.png")), Map.entry("productImages", List.of("")),
            Map.entry("productAttributes", List.of()), Map.entry("productAttributes", Map.of("stock", 10)),
            Map.entry("productAttributes", Map.of("author", List.of("invalid"))),
            Map.entry("productAttributes", Map.of("sku", "x".repeat(101))),
            Map.entry("productAttributes", Map.of("format", List.of("Paperback", " paperback "))),
            Map.entry("productAttributes", Map.of("format", List.of(1))),
            Map.entry("productAttributes", Map.of("discount", 101)), Map.entry("productAttributes", Map.of("discount", 1.234)),
            Map.entry("productId", 1), Map.entry("adminUserId", 999), Map.entry("role", "admin"))) {
            Map<String, Object> request = details(); request.put(field.getKey(), field.getValue());
            assertThat(admin.send("POST", "/api/admin/products", request).statusCode()).as("invalid field %s", field.getKey()).isEqualTo(400);
        }
        for (var request : List.of(Map.of("quantity", -1, "expectedVersion", 0, "reason", "Count"),
            Map.of("quantity", 10, "expectedVersion", 0, "reason", " "), Map.of("quantity", 10, "reason", "Count"),
            Map.of("quantity", 10, "expectedVersion", -1, "reason", "Count"), Map.of("quantity", 10.5, "expectedVersion", 0, "reason", "Count"))) {
            assertThat(admin.send("PUT", "/api/admin/products/1/stock", request).statusCode()).isEqualTo(400);
        }
        assertThat(admin.send("PUT", "/api/admin/products/1", details()).statusCode()).isEqualTo(400);
        assertThat(admin.send("PUT", "/api/admin/products/1/visibility", Map.of("expectedVersion", 0)).statusCode()).isEqualTo(400);
        assertThat(admin.send("DELETE", "/api/admin/products/1", Map.of()).statusCode()).isEqualTo(400);
        assertThat(stock(1)).isEqualTo(74);
        assertThat(version(1)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(60);
    }

    @Test
    void invalidIdsFiltersAndCategoriesReturnStructuredErrors() throws Exception {
        Browser admin = admin();
        for (String id : List.of("0", "-1", "abc", "1.5", "9223372036854775808")) {
            assertThat(admin.get("/api/admin/products/" + id).statusCode()).isEqualTo(400);
            assertThat(admin.send("DELETE", "/api/admin/products/" + id + "?expectedVersion=0", Map.of()).statusCode()).isEqualTo(400);
        }
        for (String query : List.of("page=0", "page=1.5", "page=2147483648", "visibility=unknown", "stock=unknown",
            "categoryId=-1", "lowStockThreshold=100001", "lowStockThreshold=-1", "q=" + "x".repeat(256))) {
            assertThat(admin.get("/api/admin/products?" + query).statusCode()).as(query).isEqualTo(400);
        }
        assertError(admin.get("/api/admin/products/999999"), 404, "ADMIN_PRODUCT_NOT_FOUND");
        assertError(admin.get("/api/admin/products?categoryId=999999"), 404, "ADMIN_CATEGORY_NOT_FOUND");
        Map<String, Object> request = details(); request.put("categoryId", 999999);
        assertError(admin.send("POST", "/api/admin/products", request), 404, "ADMIN_CATEGORY_NOT_FOUND");
        assertThat(ok(admin.get("/api/admin/products?page=2147483647")).get("items").size()).isZero();
        assertThat(ok(admin.get("/api/admin/products?page=2")).get("items").get(0).get("product").get("productId").asLong()).isEqualTo(45);
    }

    @Test
    void guestsCustomersAndMissingCsrfCannotManageProductsOrUploadImages() throws Exception {
        Browser guest = new Browser();
        Browser customer = customer();
        for (String path : List.of("/api/admin/products", "/api/admin/products/1", "/api/admin/categories", "/api/admin/inventory",
            "/api/admin/inventory/summary", "/api/admin/products/1/stock-history")) {
            assertThat(guest.get(path).statusCode()).isEqualTo(401);
            assertThat(customer.get(path).statusCode()).isEqualTo(403);
        }
        assertThat(guest.send("POST", "/api/admin/products", details()).statusCode()).isEqualTo(401);
        assertThat(customer.send("POST", "/api/admin/products", details()).statusCode()).isEqualTo(403);
        assertThat(customer.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", 1, "expectedVersion", 0, "reason", "Count")).statusCode()).isEqualTo(403);
        assertThat(customer.send("DELETE", "/api/admin/products/1?expectedVersion=0", Map.of()).statusCode()).isEqualTo(403);
        assertThat(guest.upload(new byte[] {1}, "image/png", "cover.png").statusCode()).isEqualTo(401);
        assertThat(customer.upload(new byte[] {1}, "image/png", "cover.png").statusCode()).isEqualTo(403);
        Browser admin = admin();
        assertThat(admin.send("POST", "/api/admin/products", details(), null).statusCode()).isEqualTo(403);
        assertThat(admin.send("PUT", "/api/admin/products/1/stock", Map.of("quantity", 1, "expectedVersion", 0, "reason", "Count"), null).statusCode()).isEqualTo(403);
        assertThat(admin.send("DELETE", "/api/admin/products/1?expectedVersion=0", Map.of(), null).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(60);
        assertThat(version(1)).isZero();
    }

    @Test
    void serviceAuthorizationAndValidationCannotBeBypassed() throws Exception {
        admin();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@example.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            assertThat(products.get("admin@example.com", 1).product().productId()).isEqualTo(1);
            assertThatThrownBy(() -> products.get("other@example.com", 1)).isInstanceOf(AccessDeniedException.class);
            var invalidStockRequest = new StockUpdateRequest(-1, 0L, "Count");
            assertThatThrownBy(() -> products.stock("admin@example.com", 1, invalidStockRequest))
                .isInstanceOf(ConstraintViolationException.class);
            var invalid = new ProductWriteRequest(1L, "Name", null, new BigDecimal("1.234"), null, null, 0, true, null);
            assertThatThrownBy(() -> products.create("admin@example.com", invalid)).isInstanceOf(ConstraintViolationException.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
            assertThatThrownBy(() -> products.get("alice@example.com", 1)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> products.delete("alice@example.com", 1, 0L)).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void corsSupportsAuthenticatedPutAndDeleteRequests() throws Exception {
        var http = HttpClient.newHttpClient();
        for (String method : List.of("PUT", "DELETE")) {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/admin/products/1"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", method).header("Access-Control-Request-Headers", "Content-Type,X-CSRF-TOKEN")
                .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElseThrow()).contains(method);
        }
    }

    private Map<String, Object> details() {
        return new HashMap<>(Map.of("categoryId", 1, "productName", "New book", "productPrice", new BigDecimal("14.25")));
    }

    private int stock(long id) { return jdbc.queryForObject("SELECT product_quantity FROM products WHERE product_id = ?", Integer.class, id); }
    private long version(long id) { return jdbc.queryForObject("SELECT version FROM products WHERE product_id = ?", Long.class, id); }

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
        browser.userId = ok(browser.send("POST", "/api/admin/auth/login", Map.of("email", "admin@example.com", "password", "correct-password")))
            .get("userId").asLong();
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
        String tokenOne = one.csrf(), tokenTwo = two.csrf();
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return one.send(methodOne, pathOne, bodyOne, tokenOne);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Request did not start");
                return two.send(methodTwo, pathTwo, bodyTwo, tokenTwo);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    private class Browser {
        long userId;
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

        void add(long productId, int quantity) throws Exception {
            ok(send("POST", "/api/cart/add", Map.of("productId", productId, "quantity", quantity, "selectedFormat", "Paperback")));
        }

        HttpResponse<String> upload(byte[] bytes, String contentType, String filename) throws Exception {
            String boundary = "boundary" + UUID.randomUUID();
            var body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(bytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/admin/product-images"))
                .header("X-CSRF-TOKEN", csrf()).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
