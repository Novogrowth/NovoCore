package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.Section;
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
 *   <li><strong>What a role receives is asserted against the bytes.</strong> A field can be blanked
 *       in a view and still reach the wire if the wrong service method was called — and since V26
 *       removed the last field restrictions, the claim being checked is that nothing IS blanked.
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

    private static final String GUARDED_ROLE = "MDIT_SUPPLIER_SKU_GUARDED";
    private static final String GUARDED_USERNAME = "master.guarded";
    private static final String GUARDED_PASSWORD = "guarded-password-long-enough";

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
    @DisplayName("what a restricted role receives, asserted against the bytes")
    class Redaction {

        @Test
        @DisplayName("Remote/Order Staff receives the supplier and its code — nothing is hidden")
        void staffSeesTheSupplier() {
            // POLICY CHANGED IN V26, and this test used to assert the opposite. There is no
            // confidentiality need around a product's purchase price or supplier in this business,
            // so Remote/Order Staff sees both. Nothing on Product is restricted from any role now.
            long supplierId = suppliers.create(
                    NewSupplier.domestic("MDIT — Visible supplier", "EL099000001")).id();
            long productId = createProduct("MDIT-REDACT-01", "{\"amount\":\"49.00\","
                    + "\"currency\":\"EUR\"}", supplierId, "SUPPLIER-CODE-XYZ");
            ApiClient.Session staff = staffSession();

            String asStaff = staff.get("/api/products/" + productId).getBody();
            String asOwner = owner.get("/api/products/" + productId).getBody();

            // Against the serialised bytes, not a view object, for the reason this class exists:
            // what reaches the wire is the only thing a client can act on.
            assertThat(asStaff).contains("SUPPLIER-CODE-XYZ");
            assertThat(asStaff).contains("\"supplierId\":" + supplierId);
            assertThat(asStaff).contains("\"amount\":\"49.00\"");

            // And the two roles now see the same product, which is the whole content of the change.
            assertThat(asStaff).isEqualTo(asOwner);
        }

        @Test
        @DisplayName("the list agrees with the single read, for both roles")
        void theListAgreesToo() {
            long supplierId = suppliers.create(
                    NewSupplier.domestic("MDIT — Visible in list", "EL099000002")).id();
            createProduct("MDIT-REDACT-02", null, supplierId, "LIST-CODE-XYZ");
            ApiClient.Session staff = staffSession();

            // The list mattered when it could disagree with the single read by calling a different
            // service method. It still matters: allFor and requireFor are separate code paths, and
            // "both show everything" is as much a claim as "both hide it" was.
            assertThat(staff.get("/api/products").getBody()).contains("LIST-CODE-XYZ");
            assertThat(owner.get("/api/products").getBody()).contains("LIST-CODE-XYZ");
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

    @Nested
    @DisplayName("substring search, over HTTP")
    class Search {

        @Test
        @DisplayName("?search= matches a name that starts with the term and one that contains it")
        void searchMatchesAnywhere() {
            // The worked example the step was approved against. Under the previous behaviour —
            // ?sku= only, an exact lookup — neither of these would have been found by "Cof".
            named("MDIT-SEARCH-1", "Coffee, Ethiopian");
            named("MDIT-SEARCH-2", "Decaf coffee blend");
            named("MDIT-SEARCH-3", "Earl Grey tea");

            String body = owner.get("/api/products?search=Cof").getBody();

            assertThat(body).contains("Coffee, Ethiopian");
            assertThat(body).contains("Decaf coffee blend");
            assertThat(body).doesNotContain("Earl Grey tea");
        }

        @Test
        @DisplayName("the brand is set over HTTP and is then findable by ?search=")
        void brandIsSetAndSearched() {
            // Both halves in one test on purpose: the PATCH route and the searched column are the
            // same feature, and a brand that stores but cannot be found is the failure worth
            // catching.
            long id = createProduct("MDIT-BRAND-1", null);
            assertThat(owner.patch("/api/products/" + id + "/brand",
                            "{\"brand\":\"Rocket Espresso\"}")
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
            named("MDIT-BRAND-2", "Unbranded house blend");

            String body = owner.get("/api/products?search=rocket").getBody();
            assertThat(body).contains("MDIT-BRAND-1");
            assertThat(body).doesNotContain("MDIT-BRAND-2");

            // Cleared, and then no longer matched — so the column really is what answered.
            assertThat(owner.patch("/api/products/" + id + "/brand", "{\"brand\":null}")
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(owner.get("/api/products?search=rocket").getBody())
                    .doesNotContain("MDIT-BRAND-1");
        }

        @Test
        @DisplayName("the exact ?sku= lookup is unchanged — the two filters coexist")
        void exactLookupDoesNotRegress() {
            // The regression this step could most easily have caused. ?sku= is what a barcode-driven
            // flow and every integration call use, and it must stay exact: a scan matching a
            // SUBSTRING of a code would put the wrong product on an invoice.
            named("MDIT-EXACT-1", "Exactly one");

            assertThat(owner.get("/api/products?sku=MDIT-EXACT-1").getBody())
                    .contains("Exactly one");
            assertThat(owner.get("/api/products?sku=MDIT-EXACT").getBody())
                    .as("a prefix of a SKU is still not a match for the exact lookup")
                    .isEqualTo("{\"items\":[]}");
        }

        @Test
        @DisplayName("search and the exact lookups are alternatives, and saying so is a 400")
        void searchIsAnAlternativeLookup() {
            ResponseEntity<String> response =
                    owner.get("/api/products?search=Cof&sku=MDIT-EXACT-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            // Named, not a bare "Bad request." — the CLAUDE.md anti-pattern about a client's
            // mistake raised as a programming error.
            assertThat(response.getBody()).contains("alternative lookups");
        }

        @Test
        @DisplayName("search combines with active, rather than replacing it")
        void searchCombinesWithActive() {
            long retired = named("MDIT-SEARCH-OFF", "Retired combiner endpoint");
            named("MDIT-SEARCH-ON", "Live combiner endpoint");
            assertThat(owner.post("/api/products/" + retired + "/deactivate", "").getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(owner.get("/api/products?search=combiner+endpoint").getBody())
                    .contains("Retired combiner endpoint", "Live combiner endpoint");
            assertThat(owner.get("/api/products?search=combiner+endpoint&active=true").getBody())
                    .contains("Live combiner endpoint")
                    .doesNotContain("Retired combiner endpoint");
        }

        @Test
        @DisplayName("suppliers and customers search the same way, case- and accent-insensitively")
        void theOtherTwoMasterDataLists() {
            assertThat(owner.post("/api/suppliers",
                            "{\"name\":\"MDIT Ολυμπία Εισαγωγές\",\"vatStatus\":\"DOMESTIC\"}")
                    .getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(owner.post("/api/customers",
                            "{\"name\":\"MDIT Αφοί Παπαδοπούλου\",\"vatStatus\":\"DOMESTIC\"}")
                    .getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // Mid-word, unaccented, and lowercase against a capitalised accented original — three
            // things the previous exact matching could not do, in one term each.
            assertThat(owner.get("/api/suppliers?search=λυμπι").getBody())
                    .contains("MDIT Ολυμπία");
            assertThat(owner.get("/api/customers?search=παπαδοπουλου").getBody())
                    .contains("MDIT Αφοί Παπαδοπούλου");
        }

        /**
         * ⚠️ This uses a <strong>purpose-built role</strong>, not Remote/Order Staff, and the reason
         * is worth recording because the first version of this test used the latter and failed.
         *
         * <p>{@code PRODUCT_SUPPLIER_SKU} still exists as a {@code ProtectedField}, but <strong>V26
         * removed the seeded restrictions</strong> — no seeded role restricts anything today, which
         * is what this class's own javadoc means by "the claim being checked is that nothing IS
         * blanked". Written against Remote/Order Staff this test would have asserted a restriction
         * that is not configured, and passed only if the guard were broken in the opposite
         * direction.
         *
         * <p>So the restriction is applied here, through the real route, on a role created for it.
         */
        @Test
        @DisplayName("a role that may not see the supplier SKU cannot find a product by it")
        void aHiddenColumnIsNotASearchableOne() {
            // Redaction blanks the field; it does not stop the row being FOUND by matching it,
            // which would disclose the value one character at a time. ProductService.searchFor
            // takes the column out of the query for a restricted viewer, and this is that,
            // asserted through the real filter chain rather than against the service.
            ResponseEntity<String> supplier = owner.post("/api/suppliers",
                    "{\"name\":\"MDIT Search Guard Supply\",\"vatStatus\":\"DOMESTIC\"}");
            long supplierId = idOf(supplier.getBody());
            createProduct("MDIT-GUARD-EP", null, supplierId, "HIDDENCODE42");

            assertThat(owner.get("/api/products?search=HIDDENCODE42").getBody())
                    .contains("MDIT-GUARD-EP");

            ApiClient.Session guarded = guardedSession();
            String hidden = guarded.get("/api/products?search=HIDDENCODE42").getBody();
            assertThat(hidden)
                    .as("a role that cannot see the supplier SKU must not be able to confirm it")
                    .isEqualTo("{\"items\":[]}");

            String visible = guarded.get("/api/products?search=MDIT-GUARD-EP").getBody();
            assertThat(visible)
                    .as("and the product is still reachable by everything the role may see")
                    .contains("MDIT-GUARD-EP");
            assertThat(visible)
                    .as("with the field redacted in the response, which is the other half")
                    .doesNotContain("HIDDENCODE42");
        }

        /** A product with a name of its own, since the shared fixture derives one from the SKU. */
        private long named(String sku, String name) {
            long id = createProduct(sku, null);
            assertThat(owner.patch("/api/products/" + id + "/name",
                            "{\"name\":\"" + name + "\"}")
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
            return id;
        }
    }

    // -------------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------------

    /**
     * A session for a role with {@code PRODUCT_SUPPLIER_SKU} restricted.
     *
     * <p>Built here rather than reusing a seeded role: V26 removed every seeded restriction, so
     * there is no longer a role in the system that restricts anything, and a test needing one has to
     * make it. VIEW on Products is all the search route requires.
     */
    private ApiClient.Session guardedSession() {
        if (users.findByUsername(GUARDED_USERNAME).isEmpty()) {
            var role = roles.findByName(GUARDED_ROLE)
                    .orElseGet(() -> roles.create(new NewRole(
                            GUARDED_ROLE, "MDIT: sees products, not supplier codes")));
            roles.grant(role.id(), Section.PRODUCTS, AccessLevel.VIEW);
            roles.restrictField(role.id(), ProtectedField.PRODUCT_SUPPLIER_SKU, true);
            users.create(new NewUser(
                    GUARDED_USERNAME, "MDIT Guarded", GUARDED_PASSWORD, role.id()));
        }
        return api.logIn(GUARDED_USERNAME, GUARDED_PASSWORD);
    }

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
