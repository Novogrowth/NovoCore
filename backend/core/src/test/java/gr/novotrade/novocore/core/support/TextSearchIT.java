package gr.novotrade.novocore.core.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.CustomerView;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductType;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.supplier.SupplierView;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Substring search, against a real PostgreSQL — which is the only thing that can answer any of it.
 *
 * <p>Every assertion here depends on {@code novocore_searchable} and on {@code pg_trgm}/
 * {@code unaccent} actually being installed. A mock repository would return whatever it was told
 * to and confirm nothing, which is the {@code CLAUDE.md} anti-pattern about a verification that
 * answers its own request. Fixtures are prefixed {@code TSIT} because these tests share one
 * non-transactional database with every other {@code *IT}.
 */
class TextSearchIT extends AbstractCoreIntegrationTest {

    @Autowired
    private ProductService products;

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private CustomerService customers;

    @Autowired
    private RoleService roles;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private JdbcTemplate jdbc;

    // -------------------------------------------------------------------------------------------
    // The normalisation function itself
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("normalisation folds case, accents and the Greek final sigma")
    void normalisation() {
        assertThat(normalise("Coffee")).isEqualTo("coffee");
        assertThat(normalise("CAFÉ")).isEqualTo("cafe");
        assertThat(normalise("Άγγελος")).isEqualTo("αγγελοσ");
        assertThat(normalise("ΑΓΓΕΛΟΣ")).isEqualTo("αγγελοσ");
        assertThat(normalise("Πελάτης Λιανικής")).isEqualTo("πελατησ λιανικησ");

        // Null in, null out — the function is STRICT, which is what lets it be used on a nullable
        // column without a coalesce at every call site.
        assertThat(normalise(null)).isNull();
    }

    @Test
    @DisplayName("the three uppercase/lowercase/final-sigma spellings of one Greek word agree")
    void finalSigmaIsNotAThirdSpelling() {
        // The case this exists for. `unaccent` does not touch ς, because it is a different letter
        // rather than an accent, and lower() maps a word-final Σ to ς rather than σ. Without the
        // fold these three produce two different answers, and which one you get depends on whether
        // the operator typed in capitals.
        // Deliberately not Set.of(...).hasSize(1): Set.of throws on a duplicate, so the passing
        // case would be an IllegalArgumentException and the failing case a green test.
        assertThat(List.of(normalise("ΠΕΛΑΤΗΣ"), normalise("Πελάτης"), normalise("πελατης")))
                .as("all three spellings must normalise to one string")
                .containsOnly("πελατησ");
    }

    @Test
    @DisplayName("this database is locale C, exactly as the real stack is")
    void theTestDatabaseMatchesTheRealOne() {
        /*
         * ⚠️ The assertion that would have caught the defect, and the reason it is here rather than
         * in a comment.
         *
         * `docker/compose.yml` initialises the real database with `--locale=C`. Testcontainers took
         * the image's own default, `en_US.utf8` — so every integration test in this repository ran
         * against a database configured unlike the one it describes. Under locale C, `lower()` folds
         * ASCII and nothing else, so the normalisation function shipped with a bare `lower()`,
         * `normalisation()` below asserted the Greek case, and it PASSED here while searching for a
         * Greek name found nothing on the real server.
         *
         * Pinning the locale is the fix; asserting it is what stops the pin being quietly removed.
         */
        assertThat(jdbc.queryForObject(
                "select datcollate from pg_database where datname = current_database()",
                String.class))
                .as("must match POSTGRES_INITDB_ARGS in docker/compose.yml")
                .isEqualTo("C");
    }

    @Test
    @DisplayName("normalisation does not depend on the database locale, and lower() alone would")
    void normalisationIsLocaleIndependent() {
        // The two halves of the same fact. A bare lower() under this locale is a no-op on Greek —
        // which is what the function used to do — and the function gets it right anyway, because it
        // names `pg_c_utf8` explicitly rather than inheriting whatever the server was built with.
        assertThat(jdbc.queryForObject("select lower('ΠΕΛΑΤΗΣ')", String.class))
                .as("under locale C a bare lower() folds ASCII and nothing else")
                .isEqualTo("ΠΕΛΑΤΗΣ");

        assertThat(normalise("ΠΕΛΑΤΗΣ")).isEqualTo("πελατησ");
    }

    @Test
    @DisplayName("the function is IMMUTABLE, without which no index on it could exist")
    void functionIsImmutable() {
        // Not a restatement of the DDL: this is the property PostgreSQL checks before it will build
        // an index on an expression, and getting it wrong is silent until CREATE INDEX fails. The
        // one-argument unaccent(text) is STABLE, which is the trap V28's comment describes.
        String volatility = jdbc.queryForObject(
                "select provolatile from pg_proc where proname = 'novocore_searchable'",
                String.class);
        assertThat(volatility).as("i = immutable, s = stable, v = volatile").isEqualTo("i");
    }

    @Test
    @DisplayName("both extensions are installed")
    void extensionsInstalled() {
        assertThat(jdbc.queryForList(
                        "select extname from pg_extension where extname in ('pg_trgm', 'unaccent')",
                        String.class))
                .containsExactlyInAnyOrder("pg_trgm", "unaccent");
    }

    // -------------------------------------------------------------------------------------------
    // The indexes
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("every searched column has a GIN trigram index on the normalised expression")
    void everySearchedColumnIsIndexed() {
        // A missing index does not break search, it makes it a sequential scan — so it fails
        // quietly, on a production-sized table, months later. This is what turns that into a build
        // failure. The list is the columns the five services actually name, spelled as columns.
        assertIndexed("product", "sku", "name", "brand", "ean", "supplier_sku");
        assertIndexed("supplier", "name", "vat_number", "email", "phone");
        assertIndexed("customer", "name", "vat_number", "email", "phone");
        assertIndexed("app_user", "username", "display_name");
        assertIndexed("app_role", "name", "description");
    }

    /**
     * ⚠️ The cast in the expected text is not decoration. Every one of these columns is
     * {@code varchar} and {@code novocore_searchable} takes {@code text}, so PostgreSQL stores and
     * reports the expression as {@code novocore_searchable((sku)::text)} — not as it was written in
     * the migration. Matching on the exact written form passes only by luck on a {@code text}
     * column, and this test was written that way first and failed against a correct index.
     */
    private void assertIndexed(String table, String... columns) {
        List<String> definitions = jdbc.queryForList(
                "select indexdef from pg_indexes where tablename = ?", String.class, table);
        for (String column : columns) {
            assertThat(definitions)
                    .as("%s.%s is searched, so it needs a GIN trigram index", table, column)
                    .anyMatch(definition -> definition.contains("USING gin")
                            && definition.contains("gin_trgm_ops")
                            && definition.replace("(" + column + ")::text", column)
                                    .contains("novocore_searchable(" + column + ")"));
        }
    }

    // -------------------------------------------------------------------------------------------
    // Matching — the behaviour the step was asked for
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("'Cof' matches a name that starts with it AND one that contains it mid-string")
    void matchesAtTheStartAndInTheMiddle() {
        // The worked example from the approval, and the whole point of the step: the previous
        // behaviour was an exact match, under which neither of these would be found.
        ProductView startsWith = product("TSIT-COF-1", "Coffee beans, Ethiopia");
        ProductView contains = product("TSIT-COF-2", "Decaf Coffee, ground");
        ProductView neither = product("TSIT-TEA-1", "Earl Grey tea");

        List<String> found = skus(products.search("Cof", false));

        assertThat(found).contains(startsWith.sku(), contains.sku());
        assertThat(found).doesNotContain(neither.sku());
    }

    @Test
    @DisplayName("matching ignores case and accents, in Greek and in Latin")
    void caseAndAccentInsensitive() {
        ProductView greek = product("TSIT-GR-1", "Καφές Αράμπικα");
        ProductView latin = product("TSIT-FR-1", "Café crème");

        assertThat(skus(products.search("ΑΡΑΜΠΙΚΑ", false))).contains(greek.sku());
        assertThat(skus(products.search("αραμπικα", false))).contains(greek.sku());
        assertThat(skus(products.search("CREME", false))).contains(latin.sku());
        assertThat(skus(products.search("crème", false))).contains(latin.sku());
    }

    @Test
    @DisplayName("a term matches any of the searched columns, not just the first")
    void searchesEveryNamedColumn() {
        SupplierView supplier = suppliers.create(
                NewSupplier.domestic("TSIT Roasters Ltd", null));
        ProductView product = products.create(new NewProduct(
                "TSIT-MULTI-1", "8901234567890", "House blend", null, ProductType.GOODS,
                anyUnitId(), anyVatClassId(), null, supplier.id(), "SUP-XYZ-99", false));

        assertThat(skus(products.search("MULTI", false))).contains(product.sku());   // sku
        assertThat(skus(products.search("house bl", false))).contains(product.sku()); // name
        assertThat(skus(products.search("123456", false))).contains(product.sku());   // ean
        assertThat(skus(products.search("xyz-99", false))).contains(product.sku());   // supplierSku
    }

    @Test
    @DisplayName("brand is searched, which is a question the catalogue could not answer before")
    void brandIsSearchable() {
        // "Which Rocket machines do we stock" had no answer at all until V29: the brand appeared in
        // a product's title only when somebody happened to type it there.
        ProductView machine = products.create(new NewProduct(
                "TSIT-BRAND-1", null, "Espresso machine, dual boiler", "Rocket Espresso",
                ProductType.GOODS, anyUnitId(), anyVatClassId(), null, null, null, false));
        ProductView unbranded = product("TSIT-BRAND-2", "House blend, bagged in store");

        assertThat(skus(products.search("rocket", false)))
                .contains(machine.sku())
                .doesNotContain(unbranded.sku());
        // Accents and case folded on this column too, through the same function.
        assertThat(skus(products.search("ROCKET ESPRESS", false))).contains(machine.sku());
    }

    @Test
    @DisplayName("a blank brand is stored as null, so it cannot half-match a product that has none")
    void blankBrandIsNull() {
        ProductView product = product("TSIT-BRAND-3", "Brandless");
        products.changeBrand(product.id(), "   ");

        assertThat(products.require(product.id()).brandIfAny()).isEmpty();
        assertThat(jdbc.queryForObject(
                "select brand from product where id = ?", String.class, product.id()))
                .as("'' must never reach the column — one representation of \"no brand\"")
                .isNull();
    }

    @Test
    @DisplayName("a blank or absent term is no filter at all, not a filter matching nothing")
    void blankTermMatchesEverything() {
        product("TSIT-BLANK-1", "Anything");

        int all = products.all().size();
        assertThat(products.search(null, false)).hasSize(all);
        assertThat(products.search("", false)).hasSize(all);
        assertThat(products.search("   ", false)).hasSize(all);
    }

    @Test
    @DisplayName("the term is trimmed, so a trailing space from a paste still matches")
    void termIsTrimmed() {
        ProductView product = product("TSIT-TRIM-1", "Trimmable");
        assertThat(skus(products.search("  Trimmable  ", false))).contains(product.sku());
    }

    @Test
    @DisplayName("wildcards in the term are matched literally, not as wildcards")
    void wildcardsAreEscaped() {
        // Without escaping, '%' matches every row and '_' matches any character — so the search box
        // would quietly answer a different question from the one asked. Not an injection: the term
        // is a bound parameter either way.
        ProductView percent = product("TSIT-PCT-1", "Blend 50% Arabica");
        ProductView plain = product("TSIT-PCT-2", "Blend 60 Robusta");

        assertThat(skus(products.search("50%", false)))
                .contains(percent.sku())
                .doesNotContain(plain.sku());

        // A lone '%' means the character, so it finds the row that literally contains one and not
        // the row that does not. Unescaped it would match every product in the catalogue — which is
        // what the second half of this asserts, in the only way that survives a shared database:
        // against a named row rather than against a total.
        assertThat(skus(products.search("%", false)))
                .as("a lone %% is a literal character, not 'everything'")
                .contains(percent.sku())
                .doesNotContain(plain.sku());
        assertThat(skus(products.search("Blend_50", false)))
                .as("'_' must not match the space in 'Blend 50%%'")
                .doesNotContain(percent.sku(), plain.sku());
    }

    @Test
    @DisplayName("activeOnly combines with the term rather than replacing it")
    void activeOnlyCombines() {
        ProductView live = product("TSIT-ACT-1", "Active combiner");
        ProductView retired = product("TSIT-ACT-2", "Retired combiner");
        products.deactivate(retired.id());

        assertThat(skus(products.search("combiner", false)))
                .contains(live.sku(), retired.sku());
        assertThat(skus(products.search("combiner", true)))
                .contains(live.sku())
                .doesNotContain(retired.sku());
    }

    @Test
    @DisplayName("a term shorter than a trigram still matches — it is just not index-accelerated")
    void shortTermsStillWork() {
        // Documented rather than refused. pg_trgm cannot extract a trigram from two characters, so
        // PostgreSQL scans; on lists of this size that is imperceptible, and refusing would be a
        // rule an operator has to learn to explain why AB behaves differently from ABC.
        ProductView product = product("TSIT-SHORT-1", "Zq marker");
        assertThat(skus(products.search("Zq", false))).contains(product.sku());
    }

    // -------------------------------------------------------------------------------------------
    // The same mechanism, on the other four entities
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("suppliers, customers and roles search their own columns through one mechanism")
    void theOtherEntities() {
        SupplierView supplier = suppliers.create(new NewSupplier(
                "TSIT Ολυμπία Εισαγωγές", "orders@tsit-olympia.example", "+30 2101234567",
                "EL914400071", VatStatus.DOMESTIC, null));
        assertThat(suppliers.search("λυμπι", false)).extracting(SupplierView::id)
                .contains(supplier.id());
        assertThat(suppliers.search("olympia.example", false)).extracting(SupplierView::id)
                .as("email is a searched column too")
                .contains(supplier.id());

        assertThat(suppliers.search("4400071", false)).extracting(SupplierView::id)
                .as("a partial ΑΦΜ, exactly as on customers — V29 reconciled the two")
                .contains(supplier.id());

        CustomerView customer = customers.create(NewCustomer.domestic(
                "TSIT Αφοί Παπαδοπούλου ΑΕ", "EL123456789"));
        assertThat(customers.search("παπαδοπουλου", false)).extracting(CustomerView::id)
                .contains(customer.id());
        assertThat(customers.search("3456789", false)).extracting(CustomerView::id)
                .as("a partial ΑΦΜ read off a document is the case this is for")
                .contains(customer.id());

        RoleView role = roles.create(new NewRole(
                "TSIT Warehouse", "Picks and packs orders in the warehouse"));
        assertThat(roles.search("warehouse", false)).extracting(RoleView::id)
                .contains(role.id());
        assertThat(roles.search("picks and packs", false)).extracting(RoleView::id)
                .as("the description is where 'which role lets somebody do X' is answered")
                .contains(role.id());
    }

    // -------------------------------------------------------------------------------------------
    // The disclosure guard
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("searchFor does not match a supplier SKU the viewer may not see")
    void restrictedColumnLeavesTheQuery() {
        // Redaction alone would blank the column and still let the row be FOUND by matching it,
        // which discloses it one character at a time — every step confirmed by a result the role is
        // entitled to see. So the column has to leave the query, not just the response.
        SupplierView supplier = suppliers.create(
                NewSupplier.domestic("TSIT Guarded Supply", null));
        ProductView product = products.create(new NewProduct(
                "TSIT-GUARD-1", null, "Guarded product", null, ProductType.GOODS,
                anyUnitId(), anyVatClassId(), null, supplier.id(), "SECRETCODE77", false));

        RoleView unrestricted = viewerSeeing(Set.of());
        RoleView restricted = viewerSeeing(Set.of(ProtectedField.PRODUCT_SUPPLIER_SKU));

        assertThat(skus(products.searchFor("SECRETCODE77", false, unrestricted)))
                .contains(product.sku());
        assertThat(skus(products.searchFor("SECRETCODE77", false, restricted)))
                .as("a hidden column must not be a way to confirm its own contents")
                .doesNotContain(product.sku());

        // And the row is still reachable by everything the role may see, so this narrows the
        // search rather than hiding the product.
        assertThat(skus(products.searchFor("GUARD", false, restricted)))
                .contains(product.sku());
    }

    @Test
    @DisplayName("hiding the supplier hides its SKU from the query too, as redaction does")
    void hidingTheSupplierHidesItsCode() {
        // Mirrors ProductView.redactedFor, which blanks the supplier SKU when EITHER field is
        // restricted: a code is meaningless without knowing whose it is and equally revealing with
        // it. The two rules must agree, or the query would search a column the response blanks.
        SupplierView supplier = suppliers.create(
                NewSupplier.domestic("TSIT Guarded Supply Two", null));
        ProductView product = products.create(new NewProduct(
                "TSIT-GUARD-2", null, "Guarded product two", null, ProductType.GOODS,
                anyUnitId(), anyVatClassId(), null, supplier.id(), "SECRETCODE88", false));

        RoleView supplierHidden = viewerSeeing(Set.of(ProtectedField.PRODUCT_SUPPLIER));

        assertThat(skus(products.searchFor("SECRETCODE88", false, supplierHidden)))
                .doesNotContain(product.sku());
    }

    // -------------------------------------------------------------------------------------------
    // The mechanism's own contract
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("searching no columns is refused as our own mistake, not answered emptily")
    void noColumnsIsRefused() {
        assertThatThrownBy(() -> TextSearch.matching("anything"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one property");
    }

    @Test
    @DisplayName("escaping is applied before the wildcards are added, not after")
    void escapeOrdering() {
        // A unit-level check of the one piece of this that is done in Java. Escaping the escape
        // character last would escape the escapes added for % and _, turning them back into
        // wildcards.
        assertThat(TextSearch.escapeLikeWildcards("a\\b")).isEqualTo("a\\\\b");
        assertThat(TextSearch.escapeLikeWildcards("50%")).isEqualTo("50\\%");
        assertThat(TextSearch.escapeLikeWildcards("a_b")).isEqualTo("a\\_b");
        assertThat(TextSearch.escapeLikeWildcards("plain")).isEqualTo("plain");
    }

    // -------------------------------------------------------------------------------------------

    private String normalise(String value) {
        return jdbc.queryForObject("select novocore_searchable(?)", String.class, value);
    }

    private ProductView product(String sku, String name) {
        return products.create(new NewProduct(
                sku, null, name, null, ProductType.GOODS,
                anyUnitId(), anyVatClassId(), null, null, null, false));
    }

    private static List<String> skus(List<ProductView> found) {
        return found.stream().map(ProductView::sku).toList();
    }

    private long anyVatClassId() {
        return vatClasses.active().getFirst().id();
    }

    private long anyUnitId() {
        return jdbc.queryForObject("select min(id) from unit_of_measure", Long.class);
    }

    /** A PRODUCTS-viewing role with the given fields restricted. */
    private static RoleView viewerSeeing(Set<ProtectedField> restricted) {
        return new RoleView(
                -1L, "TSIT viewer", null, false, false, true,
                java.util.Map.of(Section.PRODUCTS, AccessLevel.VIEW),
                restricted);
    }
}
