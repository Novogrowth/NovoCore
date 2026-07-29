package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Step 14a's master-data surface, over real HTTP with real authentication.
 *
 * <p>Four things are checked here that no unit test can, because each is a property of the whole
 * stack rather than of any one class:
 *
 * <ol>
 *   <li><strong>VIEW is not FULL.</strong> Remote/Order Staff has VIEW on Products and FULL on
 *       Customers, so the same role must be able to read a product and unable to create one. That is
 *       the whole claim of the {@code @Requires} level, and it is only true if the interceptor is
 *       actually wired into the chain.
 *   <li><strong>Redaction survives serialisation.</strong> A field can be blanked in a view and
 *       still appear in JSON if the wrong service method was called. This asserts against the bytes.
 *   <li><strong>Amounts are strings on the wire.</strong> Asserted against the raw body, because a
 *       deserialised assertion would pass either way.
 *   <li><strong>The error mapping is real.</strong> 404, 422 and 400 arrive as themselves rather
 *       than as 500s, and the 422 carries the core's own message where the 403 does not.
 * </ol>
 *
 * <p>That the application started at all is itself an assertion: {@code EndpointDeclarationCheck}
 * refuses to complete a context refresh if any {@code /api/**} handler carries no declaration, so
 * every test in this class depends on all ~70 routes being declared.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + MasterDataEndpointIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + MasterDataEndpointIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class MasterDataEndpointIT {

    static final String OWNER_USERNAME = "master.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String STAFF_USERNAME = "master.staff";
    private static final String STAFF_PASSWORD = "staff-password-long-enough";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserService users;

    @Autowired
    private RoleService roles;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private UnitOfMeasureService unitsOfMeasure;

    @Autowired
    private SupplierService suppliers;

    private ApiClient api;
    private ApiClient.Session owner;

    @BeforeEach
    void setUp() {
        api = new ApiClient(rest);
        owner = api.logIn(OWNER_USERNAME, OWNER_PASSWORD);
    }

    // -------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("the shape of every response")
    class ResponseShape {

        @Test
        @DisplayName("a list is wrapped in an items envelope, never a bare array")
        void listsAreWrapped() {
            ResponseEntity<String> response = owner.get("/api/chart-of-accounts");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            // A bare array cannot later gain a total or a cursor without breaking every caller.
            assertThat(response.getBody()).startsWith("{\"items\":[");
            assertThat(response.getBody()).contains("Cash & Cash Equivalents");
        }

        @Test
        @DisplayName("a single object is not wrapped — the envelope answers a question it never raises")
        void singleObjectsAreNotWrapped() {
            ResponseEntity<String> response = owner.get("/api/vat-classes?active=true");
            long anyVatClassId = vatClasses.active().getFirst().id();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(owner.get("/api/vat-classes/" + anyVatClassId).getBody())
                    .startsWith("{")
                    .doesNotStartWith("{\"items\"");
        }
    }

    @Nested
    @DisplayName("money on the wire")
    class MoneyOnTheWire {

        @Test
        @DisplayName("an amount is a quoted string with its currency, never a JSON number")
        void amountsAreStrings() {
            long id = createProduct("MDIT-MONEY-01", "{\"amount\":\"12.50\",\"currency\":\"EUR\"}");

            String body = owner.get("/api/products/" + id).getBody();

            assertThat(body).contains("\"amount\":\"12.50\"");
            assertThat(body).contains("\"currency\":\"EUR\"");
            // The assertion that matters: the digits never appear unquoted.
            assertThat(body).doesNotContain(":12.5");
        }

        @Test
        @DisplayName("an amount sent as a JSON number is refused with the reason, not rounded")
        void aNumericAmountIsRefused() {
            ResponseEntity<String> response = owner.post("/api/products", newProductJson(
                    "MDIT-MONEY-02", "{\"amount\":12.50,\"currency\":\"EUR\"}"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            // The rule is not guessable from a bare 400, so this 400 names it. That is the one
            // place a 400 body is not generic, and the reason is that the caller cannot fix a
            // mistake nobody described.
            assertThat(response.getBody()).contains("must be a JSON string");
        }
    }

    @Nested
    @DisplayName("permission levels")
    class PermissionLevels {

        @Test
        @DisplayName("an unauthenticated call gets 401, not a redirect")
        void unauthenticatedIsRejected() {
            assertThat(rest.getForEntity("/api/products", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Remote/Order Staff may read products — VIEW is granted")
        void staffCanReadProducts() {
            ApiClient.Session staff = staffSession();

            assertThat(staff.get("/api/products").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Remote/Order Staff may not create a product — VIEW is not FULL")
        void staffCannotCreateAProduct() {
            ApiClient.Session staff = staffSession();

            ResponseEntity<String> response = staff.post("/api/products",
                    newProductJson("MDIT-DENIED-01", null));

            // The entire claim of @Requires(level = ...). Without the interceptor reading it, this
            // would be a 201 and nothing would have complained.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody())
                    .as("a refusal must not describe the permission model")
                    .doesNotContain("PRODUCTS")
                    .doesNotContain("FULL");
        }

        @Test
        @DisplayName("Remote/Order Staff may create a customer — FULL is granted there")
        void staffCanCreateACustomer() {
            ApiClient.Session staff = staffSession();

            ResponseEntity<String> response = staff.post("/api/customers",
                    "{\"name\":\"MDIT staff-created customer\",\"vatStatus\":\"DOMESTIC\"}");

            // The other half of the same claim: the level is read per section, not applied
            // globally. A test that only proved the refusal would pass against a chain that
            // refused everything.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("Remote/Order Staff may not see the chart of accounts at all")
        void staffCannotSeeTheChart() {
            ApiClient.Session staff = staffSession();

            assertThat(staff.get("/api/chart-of-accounts").getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("a state-changing call without the CSRF token is refused")
        void csrfIsEnforced() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(HttpHeaders.COOKIE, ApiClient.SESSION_COOKIE + "=" + owner.sessionId());
            // Deliberately omitting X-XSRF-TOKEN.

            ResponseEntity<String> response = rest.exchange("/api/products", HttpMethod.POST,
                    new HttpEntity<>(newProductJson("MDIT-CSRF-01", null), headers), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("redaction, asserted against the bytes")
    class Redaction {

        @Test
        @DisplayName("Remote/Order Staff receives no supplier and no supplier SKU")
        void staffSeesNoSupplier() {
            long supplierId = suppliers.create(
                    NewSupplier.domestic("MDIT — Hidden supplier", "EL099000001")).id();
            long productId = createProduct("MDIT-REDACT-01", "{\"amount\":\"49.00\","
                    + "\"currency\":\"EUR\"}", supplierId, "SUPPLIER-CODE-XYZ");
            ApiClient.Session staff = staffSession();

            String asStaff = staff.get("/api/products/" + productId).getBody();
            String asOwner = owner.get("/api/products/" + productId).getBody();

            // Against the serialised bytes, not against a view object: a field can be blanked in a
            // view and still reach the wire if the controller called the wrong service method.
            assertThat(asStaff).doesNotContain("SUPPLIER-CODE-XYZ");
            assertThat(asStaff).doesNotContain("\"supplierId\":" + supplierId);
            // What an order picker needs is untouched.
            assertThat(asStaff).contains("\"amount\":\"49.00\"");

            assertThat(asOwner).contains("SUPPLIER-CODE-XYZ");
        }

        @Test
        @DisplayName("the product list is redacted too, not only the single read")
        void theListIsRedactedAsWell() {
            long supplierId = suppliers.create(
                    NewSupplier.domestic("MDIT — Hidden in list", "EL099000002")).id();
            createProduct("MDIT-REDACT-02", null, supplierId, "LIST-CODE-XYZ");
            ApiClient.Session staff = staffSession();

            assertThat(staff.get("/api/products").getBody()).doesNotContain("LIST-CODE-XYZ");
        }
    }

    @Nested
    @DisplayName("the error mapping")
    class ErrorMapping {

        @Test
        @DisplayName("an unknown id is 404, and the body says nothing more")
        void unknownIdIsNotFound() {
            ResponseEntity<String> response = owner.get("/api/products/9999999");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).contains("Not found");
        }

        @Test
        @DisplayName("a domain refusal is 422 and carries the core's own message")
        void aDomainRefusalIs422WithItsReason() {
            // A supplier SKU without a supplier: refused in the service and by a CHECK constraint.
            ResponseEntity<String> response = owner.post("/api/products",
                    newProductJson("MDIT-INVALID-01", null, null, "SKU-WITH-NO-SUPPLIER"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            // The asymmetry: a validation refusal explains itself, because an operator who cannot
            // see why the document was refused cannot fix it.
            assertThat(response.getBody()).containsIgnoringCase("supplier");
        }

        @Test
        @DisplayName("malformed JSON is 400, not 500")
        void malformedJsonIsBadRequest() {
            assertThat(owner.post("/api/products", "{ not json").getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("two mutually exclusive filters are refused rather than one being guessed")
        void ambiguousFiltersAreRefused() {
            // CLAUDE.md rule 7: never silently guess. Honouring one and ignoring the other would
            // answer a question nobody asked, and look like it worked.
            assertThat(owner.get("/api/products?sku=A&ean=B").getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("lookups and writes end to end")
    class EndToEnd {

        @Test
        @DisplayName("the lookups a product form needs are all reachable")
        void theLookupsAreReachable() {
            assertThat(owner.get("/api/vat-classes?active=true").getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(owner.get("/api/units-of-measure?active=true").getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(owner.get("/api/charge-types").getBody()).contains("Delivery");
            assertThat(owner.get("/api/vat-exemption-reasons").getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("create, rename, reprice, deactivate — the whole product lifecycle over HTTP")
        void theProductLifecycle() {
            long id = createProduct("MDIT-LIFE-01", null);

            assertThat(owner.patch("/api/products/" + id + "/name",
                    "{\"name\":\"MDIT renamed\"}").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(owner.patch("/api/products/" + id + "/selling-price",
                    "{\"sellingPrice\":{\"amount\":\"7.25\",\"currency\":\"EUR\"}}")
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(owner.post("/api/products/" + id + "/deactivate", null).getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            String body = owner.get("/api/products/" + id).getBody();
            assertThat(body).contains("MDIT renamed").contains("\"amount\":\"7.25\"");
            assertThat(owner.get("/api/products?active=true").getBody())
                    .doesNotContain("MDIT-LIFE-01");
            assertThat(owner.get("/api/products").getBody()).contains("MDIT-LIFE-01");
        }

        @Test
        @DisplayName("stock is readable from the product route, and carries no cost")
        void stockIsReadableFromTheProductRoute() {
            long id = createProduct("MDIT-STOCK-01", null);

            ResponseEntity<String> response = owner.get("/api/products/" + id + "/stock");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            // Quantities per location and a sellable figure — no unit cost anywhere, which is what
            // makes this route safe under PRODUCTS rather than INVENTORY.
            assertThat(response.getBody()).contains("INVENTORY").doesNotContain("unitCost");
        }

        @Test
        @DisplayName("a customer's exact VAT-number lookup and its suggestions are separate routes")
        void matchingIsSplitByCertainty() {
            owner.post("/api/customers", "{\"name\":\"MDIT Exact Match Ltd\","
                    + "\"vatNumber\":\"EL099000009\",\"vatStatus\":\"DOMESTIC\"}");

            assertThat(owner.get("/api/customers/by-vat-number/EL099000009").getBody())
                    .contains("MDIT Exact Match Ltd");
            assertThat(owner.get("/api/customers/match-suggestions?name=MDIT Exact").getBody())
                    .contains("MDIT Exact Match Ltd");
            // A blank VAT number matches nothing — it must never behave as a wildcard.
            assertThat(owner.get("/api/customers/by-vat-number/ ").getBody())
                    .isEqualTo("{\"items\":[]}");
        }

        @Test
        @DisplayName("an asset is created without a depreciation rate, and is listed as waiting")
        void anAssetWithoutARateIsListedAsWaiting() {
            ResponseEntity<String> created = owner.post("/api/assets",
                    "{\"name\":\"MDIT espresso machine\",\"acquisitionDate\":\"2026-01-15\"}");

            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            // Null means "the statutory rate is not known yet", which is the register's real state
            // while the rates are pending the accountant.
            assertThat(owner.get("/api/assets/without-depreciation-rate").getBody())
                    .contains("MDIT espresso machine");
        }
    }

    // -------------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------------

    private ApiClient.Session staffSession() {
        if (users.findByUsername(STAFF_USERNAME).isEmpty()) {
            users.create(new NewUser(STAFF_USERNAME, "MDIT Staff", STAFF_PASSWORD,
                    roles.requireByName("REMOTE_ORDER_STAFF").id()));
        }
        return api.logIn(STAFF_USERNAME, STAFF_PASSWORD);
    }

    private long createProduct(String sku, String sellingPriceJson) {
        return createProduct(sku, sellingPriceJson, null, null);
    }

    private long createProduct(
            String sku, String sellingPriceJson, Long supplierId, String supplierSku) {
        ResponseEntity<String> response = owner.post("/api/products",
                newProductJson(sku, sellingPriceJson, supplierId, supplierSku));
        assertThat(response.getStatusCode())
                .as("creating %s: %s", sku, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return idOf(response.getBody());
    }

    private String newProductJson(String sku, String sellingPriceJson) {
        return newProductJson(sku, sellingPriceJson, null, null);
    }

    private String newProductJson(
            String sku, String sellingPriceJson, Long supplierId, String supplierSku) {
        return "{"
                + "\"sku\":\"" + sku + "\","
                + "\"name\":\"" + sku + " name\","
                + "\"type\":\"GOODS\","
                + "\"unitOfMeasureId\":" + unitsOfMeasure.active().getFirst().id() + ","
                + "\"defaultVatClassId\":" + vatClasses.active().getFirst().id() + ","
                + "\"sellingPrice\":" + (sellingPriceJson == null ? "null" : sellingPriceJson) + ","
                + "\"supplierId\":" + (supplierId == null ? "null" : supplierId) + ","
                + "\"supplierSku\":" + (supplierSku == null ? "null" : "\"" + supplierSku + "\"") + ","
                + "\"serialTracked\":false"
                + "}";
    }

    /** The id out of a creation response, without pulling in a JSON mapper for one field. */
    private static long idOf(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
