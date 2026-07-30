package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductType;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * <strong>Step 15: every route in the surface, asked for by a role that may not have it.</strong>
 *
 * <p>The trading quarter drives the API as an Owner, for whom {@code RoleView.fullAccess} short
 * circuits every permission decision to {@code FULL}. So the entire narrative — fifty documents, all
 * twelve invariants — would pass identically against a system whose permission model had been
 * deleted. This class is the other half, and it is the half nothing else in the repository tests:
 * {@code MasterDataEndpointIT} proves the mechanism works on <em>four</em> routes, and step 14 built a
 * hundred and thirty-three.
 *
 * <h2>The three sweeps, and what each one can catch that the others cannot</h2>
 *
 * <ol>
 *   <li><strong>Remote/Order Staff</strong> — the real seeded role, over every route, with the
 *       expectation stated by {@link #GOVERNING_SECTION} rather than read back off the handler's own
 *       {@code @Requires}. That independence is the point: a sweep that derives its expectation from
 *       the declaration proves only that the interceptor applies the declaration, and would pass
 *       happily against a route declaring the wrong section. This is the sweep that would catch an
 *       inventory route declared {@code PRODUCTS} and handing a lot's unit cost to an order picker.
 *   <li><strong>A view-everywhere role</strong> — granted {@code VIEW} on every available section and
 *       {@code FULL} on none. Every state-changing route must refuse it. This catches the mistake the
 *       first sweep structurally cannot: a mutating handler declared {@code level = VIEW}. Staff holds
 *       {@code VIEW} on exactly one section, so outside Products it cannot tell a route that requires
 *       {@code FULL} from one that requires {@code VIEW} — both refuse it either way.
 *   <li><strong>A granted-everywhere role</strong> — {@code FULL} on every available section, by
 *       stored grants and <em>not</em> by the {@code fullAccess} flag. Every read must reach its
 *       handler. This is the direction the other two cannot test at all: a route could be guarded by a
 *       section that no grant can satisfy, and both refusal sweeps would call that a pass. It also
 *       exercises the grant path itself, which the Owner never touches.
 * </ol>
 *
 * <p>Sweeps 2 and 3 use roles <strong>created at runtime</strong>, which is possible only because
 * brief §7 made roles data and sections code. It is the same technique V26 obliges the redaction
 * tests to use, and for the same reason: a guarantee that is only ever exercised against seeded
 * configuration stops being exercised the day that configuration changes.
 *
 * <h2>Deliberately not here: what a role sees inside a response</h2>
 *
 * <p>This class is about the section boundary — whether a request reaches its handler at all. The
 * field layer, including <strong>V26's decision that a product's cost, supplier and supplier SKU are
 * visible to Remote/Order Staff</strong>, is asserted against the raw bytes by
 * {@code MasterDataEndpointIT.Redaction}, on both the single read and the list because they are
 * separate code paths. Repeating it here would be a second copy of a claim that already has a home.
 * What this class adds on that subject is the pair {@code Section.INVENTORY}'s javadoc argues for and
 * nothing asserted: stock <em>levels</em> reach an order picker, a <em>lot</em> does not.
 *
 * <h2>Its own context, on purpose</h2>
 *
 * <p>Therefore its own {@link RouteCoverage}. A sweep that touches all 133 routes must not be able to
 * inflate the trading quarter's coverage ledger, and keeping the two in separate contexts makes that
 * true by construction rather than by remembering to reset a counter in the right order.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + PermissionSweepIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + PermissionSweepIT.OWNER_PASSWORD,
            "novocore.test.scenario=permission-sweep",
        })
@Import({PostgresTestContainerConfiguration.class, RouteCoverageConfiguration.class})
@AutoConfigureTestRestTemplate
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermissionSweepIT {

    static final String OWNER_USERNAME = "sweep.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String STAFF_USERNAME = "sweep.staff";
    private static final String VIEWER_USERNAME = "sweep.viewer";
    private static final String GRANTEE_USERNAME = "sweep.grantee";
    private static final String BODY_PROBE_USERNAME = "sweep.bodyprobe";
    private static final String BARE_REFUSAL_USERNAME = "sweep.barerefusal";
    private static final String PASSWORD = "role-password-long-enough";

    /** For a path variable on a route this role cannot reach — the handler never runs, so it is
     * irrelevant what it names. Chosen far outside any sequence so that if a handler <em>does</em>
     * run, it answers 404 rather than acting on somebody's real record. */
    private static final long UNREACHABLE_ID = 999_999_999L;

    private static final String SWEEP_VAT_NUMBER = "EL099500001";

    @Autowired private TestRestTemplate rest;
    @Autowired private RouteCoverage coverage;
    @Autowired private UserService users;
    @Autowired private RoleService roles;
    @Autowired private ProductService products;
    @Autowired private CustomerService customers;
    @Autowired private SupplierService suppliers;
    @Autowired private UnitOfMeasureService unitsOfMeasure;
    @Autowired private VatClassService vatClasses;

    private ApiClient api;
    private long productId;
    private long customerId;
    private long probeProductId;
    private long probeCustomerId;

    // ===================================================================================
    // The route-to-section table — the independent half of the expectation
    // ===================================================================================

    /**
     * Which {@link Section} governs each family of routes, stated here rather than read off the
     * handler.
     *
     * <p><strong>Ordered, first match wins</strong>, because two of these genuinely overlap and the
     * overlap is a real decision rather than an accident of naming: <em>recording</em> a credit note
     * is {@code SALES} — it is a document correcting a sale — while <em>allocating</em> one is
     * {@code SETTLEMENTS}, because an allocation is a statement about who owes what and reaches the
     * open-item layer. Same for a customer credit. The specific rules therefore come first, and a
     * reader who reorders this list will find out.
     *
     * <p>Every route must match exactly one entry and every entry must match at least one route —
     * both asserted, so a new family cannot arrive unclassified and a rule cannot outlive the routes
     * it described.
     */
    private static final Map<String, SectionRule> GOVERNING_SECTION = orderedRules();

    private record SectionRule(Predicate<String> matches, Section section) {
    }

    private static Map<String, SectionRule> orderedRules() {
        Map<String, SectionRule> rules = new LinkedHashMap<>();

        // The two deliberate overlaps, most specific first. See the field javadoc.
        rules.put("credit note allocations", prefix("/api/credit-notes/{id}/allocations",
                Section.SETTLEMENTS));
        rules.put("customer credit allocations", prefix("/api/customer-credits/{id}/allocations",
                Section.SETTLEMENTS));

        rules.put("the chart of accounts", startingWith(Section.CHART_OF_ACCOUNTS,
                "/api/chart-of-accounts", "/api/accounts", "/api/account-groups"));
        rules.put("the fixed asset register", startingWith(Section.FIXED_ASSETS, "/api/assets"));
        rules.put("customers", startingWith(Section.CUSTOMERS, "/api/customers"));
        rules.put("the email outbox", startingWith(Section.EMAIL_OUTBOX, "/api/email/"));
        rules.put("inventory", startingWith(Section.INVENTORY, "/api/inventory/"));

        // /api/units-of-measure sits under PRODUCTS because it is the picker a product form needs;
        // it is a lookup, not a section of its own.
        rules.put("products and bundles", startingWith(Section.PRODUCTS,
                "/api/products", "/api/bundles", "/api/units-of-measure"));

        rules.put("purchasing", startingWith(Section.PURCHASING,
                "/api/purchase-invoices", "/api/purchase-invoice-lines", "/api/goods-receipts",
                "/api/goods-receipt-lines", "/api/freight-allocations"));
        rules.put("sales", startingWith(Section.SALES, "/api/sales-invoices", "/api/credit-notes"));
        rules.put("settlements", startingWith(Section.SETTLEMENTS,
                "/api/settlements", "/api/bank-transfers", "/api/allocations", "/api/open-items",
                "/api/customer-credits"));
        rules.put("suppliers", startingWith(Section.SUPPLIERS, "/api/suppliers"));
        rules.put("tax and charges", startingWith(Section.TAX_AND_CHARGES,
                "/api/vat-classes", "/api/vat-exemption-reasons", "/api/charge-types"));

        return rules;
    }

    private static SectionRule prefix(String exact, Section section) {
        return new SectionRule(pattern -> pattern.equals(exact), section);
    }

    private static SectionRule startingWith(Section section, String... prefixes) {
        return new SectionRule(
                pattern -> List.of(prefixes).stream().anyMatch(pattern::startsWith), section);
    }

    /**
     * What a route requires, from its verb alone.
     *
     * <p>Deliberately not read from {@code @Requires}: "a request that changes state needs
     * {@code FULL}" is the rule the declarations are supposed to express, so asserting it against the
     * declarations would be asserting them against themselves.
     */
    private static AccessLevel levelRequiredBy(String httpMethod) {
        return "GET".equals(httpMethod) ? AccessLevel.VIEW : AccessLevel.FULL;
    }

    private static Section sectionGoverning(String pattern) {
        for (SectionRule rule : GOVERNING_SECTION.values()) {
            if (rule.matches().test(pattern)) {
                return rule.section();
            }
        }
        return null;
    }

    // ===================================================================================
    // Fixtures
    // ===================================================================================

    @BeforeAll
    void setUp() {
        api = new ApiClient(rest);

        long unitId = unitsOfMeasure.active().getFirst().id();
        long vatClassId = vatClasses.active().getFirst().id();
        long supplierId = suppliers.create(
                NewSupplier.domestic("SWEEP — Supplier", "EL099500009")).id();

        // A product carrying every once-protected field, so a reachable Products read returns
        // something with substance rather than a row of nulls.
        productId = products.create(new NewProduct(
                "SWEEP-PRODUCT-01", null, "SWEEP product", ProductType.GOODS, unitId, vatClassId,
                Money.ofEur("12.00"), supplierId, "SWEEP-SUPPLIER-CODE", false)).id();

        // A throwaway customer, because Remote/Order Staff holds FULL on Customers and the sweep
        // therefore really does deactivate and rename it. Nothing else may depend on its state.
        customerId = customers.create(new NewCustomer(
                "SWEEP — Throwaway customer", null, null, SWEEP_VAT_NUMBER,
                VatStatus.DOMESTIC, null, null)).id();

        // A second pair, for the empty-body probe alone. Some of the routes it drives take no body
        // and therefore succeed; keeping their targets separate is what stops one sweep quietly
        // changing what another one is asserting about.
        probeProductId = products.create(new NewProduct(
                "SWEEP-PRODUCT-PROBE", null, "SWEEP body probe product", ProductType.GOODS,
                unitId, vatClassId, Money.ofEur("12.00"), supplierId, "PROBE-CODE", false)).id();
        probeCustomerId = customers.create(new NewCustomer(
                "SWEEP — Body probe customer", null, null, "EL099500002",
                VatStatus.DOMESTIC, null, null)).id();
    }

    // ===================================================================================
    // Sweep 1 — the real role
    // ===================================================================================

    @Nested
    @DisplayName("Remote/Order Staff, over every route in the surface")
    class TheSeededRole {

        @Test
        @DisplayName("every route answers exactly as the section table says it must")
        void everyRouteMatchesTheTable() {
            RoleView staff = roles.requireByName("REMOTE_ORDER_STAFF");
            ApiClient.Session session = sessionFor(STAFF_USERNAME, staff.id());

            List<String> wrong = new ArrayList<>();
            Map<String, Integer> refusedPerSection = new TreeMap<>();
            int granted = 0;

            for (RouteCoverage.Route route : coverage.allRoutes()) {
                Section section = sectionGoverning(route.pattern());
                if (section == null) {
                    // Reported by theSectionTableIsExhaustiveAndHasNoDeadRules, which says it
                    // better; skipping here keeps this test's failure about permissions.
                    continue;
                }
                AccessLevel required = levelRequiredBy(route.method());
                boolean shouldReach = required == AccessLevel.VIEW
                        ? staff.canView(section)
                        : staff.canEdit(section);

                ResponseEntity<String> response = send(session, route);
                HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());

                if (shouldReach) {
                    granted++;
                    if (status == HttpStatus.FORBIDDEN || status == HttpStatus.UNAUTHORIZED) {
                        wrong.add(route + " — the table says " + section + " at " + required
                                + ", which this role holds, but the API answered " + status
                                + ": " + response.getBody());
                    }
                } else {
                    refusedPerSection.merge(section.name(), 1, Integer::sum);
                    if (status != HttpStatus.FORBIDDEN) {
                        wrong.add(route + " — the table says " + section + " at " + required
                                + ", which this role does not hold, but the API answered " + status
                                + " instead of 403: " + response.getBody());
                    }
                    assertRefusalDisclosesNothing(route, section, response);
                }
            }

            assertThat(wrong)
                    .as("""
                            Routes whose permission behaviour disagrees with the section table. \
                            Either the route's @Requires names the wrong section or level, or the \
                            table in this class is out of date — and which of those it is, is \
                            exactly the question worth being forced to answer.""")
                    .isEmpty();

            // Neither half may be empty, or one mistake in the table would make the whole sweep
            // vacuous while still reporting 133 routes examined.
            assertThat(granted)
                    .as("Remote/Order Staff must reach something, or this sweep proves only that "
                            + "the session was broken")
                    .isPositive();
            assertThat(refusedPerSection.values().stream().mapToInt(Integer::intValue).sum())
                    .as("Remote/Order Staff must be refused something")
                    .isPositive();

            System.out.println("Remote/Order Staff reached " + granted + " of "
                    + coverage.allRoutes().size() + " routes; refusals by section: "
                    + refusedPerSection);
        }

        @Test
        @DisplayName("every route is classified, and no rule describes routes that no longer exist")
        void theSectionTableIsExhaustiveAndHasNoDeadRules() {
            List<String> unclassified = new ArrayList<>();
            Map<String, Integer> hitsPerRule = new LinkedHashMap<>();
            GOVERNING_SECTION.keySet().forEach(name -> hitsPerRule.put(name, 0));

            for (RouteCoverage.Route route : coverage.allRoutes()) {
                String matched = null;
                for (Map.Entry<String, SectionRule> rule : GOVERNING_SECTION.entrySet()) {
                    if (rule.getValue().matches().test(route.pattern())) {
                        matched = rule.getKey();
                        break;
                    }
                }
                if (matched == null) {
                    unclassified.add(route.toString());
                } else {
                    hitsPerRule.merge(matched, 1, Integer::sum);
                }
            }

            assertThat(unclassified)
                    .as("""
                            Routes this class has no opinion about. An unclassified route is one \
                            the sweep silently skips, which is the failure mode the whole design is \
                            against: it would report 133 routes examined while examining fewer.""")
                    .isEmpty();

            assertThat(hitsPerRule)
                    .as("a rule matching nothing describes a family of routes that has been renamed "
                            + "or removed, and it will go on passing while covering nothing")
                    .allSatisfy((name, hits) -> assertThat(hits).isPositive());
        }

        /**
         * The withholding half of {@code WebExceptionHandler}'s policy, applied to every refusal at
         * once rather than to one sampled route.
         *
         * <p>A 403 that named the section would confirm to a caller that it exists and describe the
         * permission model to somebody probing it. The message that does say those things is written
         * for the log, where it belongs.
         */
        private void assertRefusalDisclosesNothing(
                RouteCoverage.Route route, Section section, ResponseEntity<String> response) {
            String body = response.getBody() == null ? "" : response.getBody();
            assertThat(body)
                    .as("%s — the refusal named the section it was refused for", route)
                    .doesNotContain(section.name());
            assertThat(body)
                    .as("%s — the refusal named the role", route)
                    .doesNotContain("REMOTE_ORDER_STAFF");
            assertThat(body)
                    .as("%s — the refusal described the permission model", route)
                    .doesNotContain("FULL", "VIEW");
        }
    }

    // ===================================================================================
    // Sweep 2 — a role that can see everything and change nothing
    // ===================================================================================

    @Test
    @DisplayName("a view-everywhere role is refused by every state-changing route")
    void viewingEverythingChangesNothing() {
        RoleView viewer = roleWith(AccessLevel.VIEW, "SWEEP_VIEW_EVERYWHERE");
        ApiClient.Session session = sessionFor(VIEWER_USERNAME, viewer.id());

        List<String> reached = new ArrayList<>();
        int swept = 0;
        for (RouteCoverage.Route route : coverage.allRoutes()) {
            if ("GET".equals(route.method())) {
                continue;
            }
            swept++;
            ResponseEntity<String> response = send(session, route);
            if (response.getStatusCode().value() != HttpStatus.FORBIDDEN.value()) {
                reached.add(route + " answered " + response.getStatusCode()
                        + " for a role holding VIEW and nothing more: " + response.getBody());
            }
        }

        assertThat(reached)
                .as("""
                        State-changing routes that a view-only role got past. Each is a handler \
                        declaring @Requires(level = VIEW) while changing something — a mistake that \
                        looks exactly like working code, is invisible to the Remote/Order Staff \
                        sweep outside Products, and hands write access to every read-only role in \
                        the system.""")
                .isEmpty();
        assertThat(swept)
                .as("the surface has state-changing routes; sweeping none would pass vacuously")
                .isGreaterThan(50);
    }

    // ===================================================================================
    // Sweep 3 — a role granted everything, by grants rather than by the full-access flag
    // ===================================================================================

    @Test
    @DisplayName("a role granted every section by grants reaches every read")
    void grantingEverySectionReachesEveryRead() {
        RoleView grantee = roleWith(AccessLevel.FULL, "SWEEP_GRANTED_EVERYWHERE");
        assertThat(grantee.fullAccess())
                .as("this must be a grant-driven role; the fullAccess shortcut is what it is here "
                        + "to avoid testing, since Owner and Admin already take that path")
                .isFalse();
        ApiClient.Session session = sessionFor(GRANTEE_USERNAME, grantee.id());

        List<String> refused = new ArrayList<>();
        for (RouteCoverage.Route route : coverage.allRoutes()) {
            if (!"GET".equals(route.method())) {
                continue;
            }
            ResponseEntity<String> response = send(session, route);
            if (response.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                refused.add(route + ": " + response.getBody());
            }
        }

        assertThat(refused)
                .as("""
                        Reads that no grant can reach. A route guarded by a section that cannot be \
                        granted is unreachable by every role except the two system ones, and both \
                        refusal sweeps above would call that a pass — this is the only direction \
                        that catches it.""")
                .isEmpty();
    }

    // ===================================================================================
    // What a reachable route does with a request that is missing its fields
    // ===================================================================================

    /**
     * <strong>A form submitted with its fields empty must be refused, not fail.</strong>
     *
     * <p>Whether a route is <em>reachable</em> and whether it <em>works</em> are separate claims,
     * asserted separately so a failure says which one broke. This one is driven by the
     * granted-everywhere role rather than by Remote/Order Staff, because staff reaches twenty routes
     * and the other hundred and thirteen would otherwise never be asked the question at all.
     *
     * <p>A 400 or a 422 is the right answer here: the caller sent a body it can correct. A 500 means a
     * missing field reached an {@code Objects.requireNonNull} written to catch a <em>programming</em>
     * error, and the caller got a stack trace's worth of nothing — in Boot's legacy
     * {@code {timestamp,status,error,path}} shape rather than the RFC 7807 body every other refusal on
     * this surface uses, so it is also the one response a client cannot parse uniformly. That is step
     * 15's defect 5 again, one exception class along.
     *
     * <p><strong>Its own product and customer</strong>, so that a route which does act on a real id —
     * {@code POST /api/products/{id}/deactivate} takes no body at all and therefore succeeds — changes
     * nothing any other test in this class depends on.
     */
    @Test
    @DisplayName("no route answers a request with missing fields by failing")
    void noRouteFailsOnAnEmptyBody() {
        RoleView grantee = roleWith(AccessLevel.FULL, "SWEEP_EMPTY_BODY_PROBE");
        ApiClient.Session session = sessionFor(BODY_PROBE_USERNAME, grantee.id());

        List<String> exploded = new ArrayList<>();
        for (RouteCoverage.Route route : coverage.allRoutes()) {
            if ("GET".equals(route.method()) || "DELETE".equals(route.method())) {
                continue;
            }
            ResponseEntity<String> response =
                    sendTo(session, route, pathFor(route.pattern(), probeProductId, probeCustomerId));
            if (response.getStatusCode().is5xxServerError()) {
                exploded.add(route + " answered " + response.getStatusCode()
                        + " to an empty body: " + response.getBody());
            }
        }

        assertThat(exploded)
                .as("""
                        Routes that answered a request with missing fields by failing rather than \
                        by refusing. A 500 tells a client nothing it can act on, puts a stack trace \
                        in the log for what is an ordinary user mistake, and is the one response \
                        shape on this surface that is not RFC 7807.""")
                .isEmpty();
    }

    /**
     * <strong>No route refuses a caller with {@code "Bad request."} and nothing else.</strong>
     *
     * <p>The behavioural half of the guard whose structural half is
     * {@code WebAuthorizationRulesTest.clientMistakesAreNotProgrammingErrors}. That rule catches the
     * exception being <em>constructed</em> in the web layer; this catches the symptom however it
     * arises — an {@code IllegalArgumentException} thrown by a service the controller called, a null
     * unboxed, an {@code Optional.orElseThrow} with the wrong supplier. ArchUnit cannot see any of
     * those, and each produces the identical useless response.
     *
     * <p>{@code "Bad request."} is the exact string {@code WebExceptionHandler} substitutes when it
     * decides a message describes our internal state rather than the caller's request. Reaching it is
     * never right from a route driven the way a client drives one: either the caller was told what to
     * fix, or the request was fine and something else answered.
     *
     * <p><strong>Every route, both shapes.</strong> Reads are asked with no query parameters at all —
     * which is what defect 5 was: nine listings that cannot answer without one, each with a carefully
     * written message that nobody received. Writes are asked with an empty body. Between them that is
     * all 133.
     */
    @Test
    @DisplayName("no route refuses with a bare \"Bad request.\" and no reason")
    void noRouteRefusesWithoutSayingWhy() {
        RoleView grantee = roleWith(AccessLevel.FULL, "SWEEP_BARE_REFUSAL_PROBE");
        ApiClient.Session session = sessionFor(BARE_REFUSAL_USERNAME, grantee.id());

        List<String> silent = new ArrayList<>();
        for (RouteCoverage.Route route : coverage.allRoutes()) {
            ResponseEntity<String> response =
                    sendTo(session, route, pathFor(route.pattern(), probeProductId, probeCustomerId));
            String body = response.getBody() == null ? "" : response.getBody();
            if (body.contains("\"detail\":\"Bad request.\"")) {
                silent.add(route + " answered " + response.getStatusCode()
                        + " with no reason: " + body);
            }
        }

        assertThat(silent)
                .as("""
                        Routes that refused a caller and told them nothing. Whatever was wrong, the \
                        message describing it was written and then discarded — which is step 15's \
                        defect 5, and a route whose only correct usage cannot be discovered from its \
                        own error is one a frontend gets written against by guesswork.""")
                .isEmpty();
    }

    // ===================================================================================
    // The one field-layer claim that has no other home
    // ===================================================================================

    @Test
    @DisplayName("an order picker sees how many are left, and not what they cost")
    void stockLevelsReachTheFloorAndLotsDoNot() {
        ApiClient.Session staff =
                sessionFor(STAFF_USERNAME, roles.requireByName("REMOTE_ORDER_STAFF").id());

        // This pair is the entire argument for INVENTORY being a section separate from PRODUCTS,
        // and it was written down in Section's javadoc and asserted nowhere. V26 removed the field
        // restrictions that used to be the other half of it, which makes the section split the only
        // thing still keeping a lot's unit cost away from this role.
        ResponseEntity<String> stock = staff.get("/api/products/" + productId + "/stock");
        assertThat(stock.getStatusCode())
                .as("stock levels are a product-level read an order picker needs")
                .isEqualTo(HttpStatus.OK);
        assertThat(stock.getBody())
                .as("and the stock route carries no cost, which is what makes it safe here")
                .doesNotContain("unitCost");

        assertThat(staff.get("/api/inventory/lots?productId=" + productId).getStatusCode())
                .as("a lot carries its unit cost, so the same role must not reach it")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ===================================================================================
    // Driving one route
    // ===================================================================================

    /**
     * Sends one request against a route.
     *
     * <p><strong>The body is deliberately meaningless</strong> for anything that takes one. This
     * class asks whether a request reaches its handler, and the permission check runs in
     * {@code preHandle} — before argument resolution, so before the body is ever parsed. A valid body
     * would therefore change nothing about what is under test while making 133 routes' worth of
     * request shapes something this class had to know and keep up to date. Where a granted route is
     * reached, {@code {}} produces a 400 or a 422 from the handler, and "not 403, not 401, not 5xx"
     * is exactly the claim being made.
     */
    private ResponseEntity<String> send(ApiClient.Session session, RouteCoverage.Route route) {
        return sendTo(session, route, pathFor(route.pattern(), productId, customerId));
    }

    private ResponseEntity<String> sendTo(
            ApiClient.Session session, RouteCoverage.Route route, String path) {
        return switch (route.method()) {
            case "GET" -> session.get(path);
            case "POST" -> session.post(path, "{}");
            case "PUT" -> session.put(path, "{}");
            case "PATCH" -> session.patch(path, "{}");
            case "DELETE" -> session.delete(path);
            default -> throw new AssertionError(
                    "A route mapped to " + route.method() + " (" + route + ") — this sweep does not "
                            + "know how to drive it. An 'ANY' here means a handler names no HTTP "
                            + "method and therefore answers all of them, which is worth looking at "
                            + "rather than working around.");
        };
    }

    /**
     * A concrete path for a route pattern.
     *
     * <p>Real ids only where Remote/Order Staff can actually reach the handler. Everywhere else the
     * handler never runs, so the value cannot matter — and {@link #UNREACHABLE_ID} is chosen so that
     * if one ever does run, it acts on nothing.
     */
    private String pathFor(String pattern, long product, long customer) {
        String path = pattern;
        if (path.startsWith("/api/customers")) {
            path = path.replace("{id}", String.valueOf(customer))
                    .replace("{vatNumber}", SWEEP_VAT_NUMBER);
        } else if (path.startsWith("/api/products") || path.startsWith("/api/bundles")) {
            path = path.replace("{id}", String.valueOf(product));
        }
        path = path.replaceAll("\\{[^}]+}", String.valueOf(UNREACHABLE_ID));

        // Query parameters a route cannot answer without. Only the reachable ones need these: a
        // refused route never gets far enough to notice they are missing.
        if (path.equals("/api/customers/match-suggestions")) {
            path += "?name=SWEEP";
        }
        return path;
    }

    // ===================================================================================
    // Roles and sessions
    // ===================================================================================

    /** A non-system role holding one level on every section that has something built behind it. */
    private RoleView roleWith(AccessLevel level, String name) {
        RoleView role = roles.create(new NewRole(name, name + " — created by the permission sweep"));
        for (Section section : Section.values()) {
            if (section.isAvailable()) {
                role = roles.grant(role.id(), section, level);
            }
        }
        return role;
    }

    private ApiClient.Session sessionFor(String username, long roleId) {
        if (users.findByUsername(username).isEmpty()) {
            users.create(new NewUser(username, username, PASSWORD, roleId));
        }
        return api.logIn(username, PASSWORD);
    }
}
