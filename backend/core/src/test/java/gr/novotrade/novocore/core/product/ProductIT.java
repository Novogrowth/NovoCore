package gr.novotrade.novocore.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.product.InvalidProductException;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductNotFoundException;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductType;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.SectionAccessDeniedException;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.supplier.SupplierView;
import gr.novotrade.novocore.core.api.tax.VatClassPrecedence;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassSource;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Products, against a real PostgreSQL — including the two things step 5 was blocked on: Q5's
 * supplier link, and step 4's field-restriction obligation applied against a real role loaded from
 * the database rather than a hand-built one.
 */
class ProductIT extends AbstractCoreIntegrationTest {

    @Autowired
    private ProductService products;

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private UnitOfMeasureService unitsOfMeasure;

    @Autowired
    private RoleService roles;

    @Autowired
    private JdbcTemplate jdbc;

    private long standardRateId() {
        return vatClasses.requireByCode("1410").id();
    }

    /** Units come from the V11 lookup table now (Q34), not from an enum. */
    private long pieceId() {
        return unitsOfMeasure.requireByCode("PIECE").id();
    }

    private long kilogramId() {
        return unitsOfMeasure.requireByCode("KILOGRAM").id();
    }

    @Test
    @DisplayName("a product round-trips, with the price reassembled as Money")
    void createAndRead() {
        ProductView created = products.create(NewProduct.goods(
                "ProdIT-ESP-01", "ProdIT espresso machine", pieceId(), standardRateId(),
                Money.ofEur("899.00")));

        assertThat(created.sku()).isEqualTo("ProdIT-ESP-01");
        assertThat(created.type()).isEqualTo(ProductType.GOODS);
        assertThat(created.isStocked()).isTrue();
        assertThat(created.active()).isTrue();

        ProductView read = products.requireBySku("ProdIT-ESP-01");
        // Stored as two columns and reassembled, since Money cannot be an @Embeddable without
        // core-api depending on jakarta.persistence.
        assertThat(read.sellingPriceIfAny()).contains(Money.ofEur("899.00"));
        assertThat(read.sellingPrice().currency().getCurrencyCode()).isEqualTo("EUR");

        assertThat(products.findBySku("prodit-esp-01"))
                .as("SKU lookup is case-insensitive")
                .isPresent();
    }

    @Test
    @DisplayName("neither stock nor last purchase price is a column (brief §5, Q6)")
    void derivedValuesAreNotStored() {
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'product'
                """, String.class))
                .doesNotContain("stock", "stock_quantity", "quantity_on_hand",
                        "last_purchase_price")
                // Rule 2 again, asserted rather than trusted.
                .noneSatisfy(column -> assertThat(column.toLowerCase())
                        .containsAnyOf("go_", "woo", "external"));

        // Present on the view but empty: derived from lot costs, and lots arrive in step 6.
        ProductView product = products.create(NewProduct.goods(
                "ProdIT-DERIVED-01", "ProdIT derived values", pieceId(), standardRateId(),
                Money.ofEur("10.00")));
        assertThat(product.lastPurchasePriceIfAny()).isEmpty();
    }

    @Test
    @DisplayName("the first monetary column carries its currency, and cannot exist without it")
    void moneyColumnHasACurrencyCompanion() {
        // ADR 0005 made structural. This is the schema's first monetary column, so it is where the
        // amount/currency pairing convention starts being enforced rather than described.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO product (sku, name, product_type, unit_of_measure_id,
                                     default_vat_class_id, selling_price)
                VALUES ('ProdIT-PROBE-NOCCY', 'Probe: amount without currency', 'GOODS', ?,
                        ?, 10.00)
                """, pieceId(), standardRateId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("product_selling_price_has_currency");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO product (sku, name, product_type, unit_of_measure_id,
                                     default_vat_class_id, selling_price_currency)
                VALUES ('ProdIT-PROBE-CCYONLY', 'Probe: currency without amount', 'GOODS', ?,
                        ?, 'EUR')
                """, pieceId(), standardRateId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("product_selling_price_has_currency");
    }

    @Test
    @DisplayName("a zero price is refused; no price at all is how \"not priced yet\" is said")
    void zeroPriceIsRefused() {
        // Zero and unset look identical on a screen, and zero produces an invoice line worth
        // nothing without anyone deciding to give the goods away.
        assertThatExceptionOfType(InvalidProductException.class)
                .isThrownBy(() -> products.create(NewProduct.goods(
                        "ProdIT-ZERO-01", "ProdIT zero price", pieceId(), standardRateId(),
                        Money.ofEur("0.00"))))
                .withMessageContaining("silently free sale");

        // Unpriced is allowed: an imported or barcode-first product may not have a price yet.
        ProductView unpriced = products.create(NewProduct.goods(
                "ProdIT-UNPRICED-01", "ProdIT unpriced", pieceId(), standardRateId(), null));
        assertThat(unpriced.sellingPriceIfAny()).isEmpty();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO product (sku, name, product_type, unit_of_measure_id,
                                     default_vat_class_id, selling_price, selling_price_currency)
                VALUES ('ProdIT-PROBE-ZERO', 'Probe: zero price', 'GOODS', ?, ?, 0.00, 'EUR')
                """, pieceId(), standardRateId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("product_selling_price_positive");
    }

    // ---------------------------------------------------------------------------------------
    // Q5 — the supplier link
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("one product has one supplier, with the supplier's own code alongside it (Q5)")
    void supplierLinkIsASingleReference() {
        SupplierView importer = suppliers.create(NewSupplier.domestic(
                "ProdIT — Importer", "EL066666001"));

        ProductView product = products.create(new NewProduct(
                "ProdIT-SUP-01", null, "ProdIT supplied item", ProductType.GOODS,
                kilogramId(), standardRateId(), Money.ofEur("24.00"),
                importer.id(), "IMP-77-A"));

        assertThat(product.supplier()).contains(importer.id());
        assertThat(product.supplierSkuIfAny()).contains("IMP-77-A");
        assertThat(products.bySupplier(importer.id()))
                .extracting(ProductView::sku).contains("ProdIT-SUP-01");

        // One reference, not a collection: Q5 answered as one product, one supplier, and the
        // schema says so too — a single nullable bigint column rather than a join table.
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'product'
                  AND column_name LIKE '%supplier%'
                ORDER BY column_name
                """, String.class))
                .containsExactly("supplier_id", "supplier_sku");
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
                """, String.class))
                .as("no many-to-many join table (Q5: if this needs to change, that is a future "
                        + "version, not now)")
                .doesNotContain("product_supplier", "product_suppliers");
    }

    @Test
    @DisplayName("a supplier SKU without a supplier is refused — the point of Q5")
    void supplierSkuNeedsASupplier() {
        assertThatExceptionOfType(InvalidProductException.class)
                .isThrownBy(() -> products.create(new NewProduct(
                        "ProdIT-ORPHAN-01", null, "ProdIT orphan code", ProductType.GOODS,
                        pieceId(), standardRateId(), null, null, "ORPHAN-1")))
                .withMessageContaining("identifies nothing without knowing whose code it is");

        // And in the database, not only in Java.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO product (sku, name, product_type, unit_of_measure_id,
                                     default_vat_class_id, supplier_sku)
                VALUES ('ProdIT-PROBE-ORPHAN', 'Probe: orphan supplier code', 'GOODS', ?,
                        ?, 'ORPHAN-PROBE')
                """, pieceId(), standardRateId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("product_supplier_sku_needs_supplier");
    }

    @Test
    @DisplayName("a supplier with no supplier SKU is ordinary, and both clear together")
    void supplierWithoutSupplierSku() {
        SupplierView supplier = suppliers.create(NewSupplier.domestic(
                "ProdIT — Own reference supplier", "EL066666002"));

        ProductView product = products.create(new NewProduct(
                "ProdIT-SUP-02", null, "ProdIT own reference", ProductType.GOODS,
                pieceId(), standardRateId(), null, supplier.id(), null));
        assertThat(product.supplierSkuIfAny()).isEmpty();

        // Clearing the supplier clears its code with it — one setter, so the code cannot outlive
        // the supplier it belongs to.
        ProductView cleared = products.changeSupplier(product.id(), null, null);
        assertThat(cleared.supplier()).isEmpty();
        assertThat(cleared.supplierSkuIfAny()).isEmpty();

        assertThatExceptionOfType(InvalidProductException.class)
                .isThrownBy(() -> products.changeSupplier(product.id(), null, "STILL-HERE"))
                .withMessageContaining("needs a supplier");
    }

    @Test
    @DisplayName("an unknown or inactive supplier is refused by name")
    void unknownOrInactiveSupplierIsRefused() {
        SupplierView retired = suppliers.create(NewSupplier.domestic(
                "ProdIT — Retired supplier", "EL066666003"));
        suppliers.deactivate(retired.id());

        assertThatExceptionOfType(InvalidProductException.class)
                .isThrownBy(() -> products.create(new NewProduct(
                        "ProdIT-BADSUP-01", null, "ProdIT unknown supplier", ProductType.GOODS,
                        pieceId(), standardRateId(), null, 999_999L, null)))
                .withMessageContaining("No supplier with id 999999");

        assertThatExceptionOfType(InvalidProductException.class)
                .isThrownBy(() -> products.create(new NewProduct(
                        "ProdIT-BADSUP-02", null, "ProdIT inactive supplier", ProductType.GOODS,
                        pieceId(), standardRateId(), null, retired.id(), null)))
                .withMessageContaining("inactive");
    }

    // ---------------------------------------------------------------------------------------
    // Step 4's obligation, against the real seeded role
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("Remote/Order Staff cannot see cost or supplier on a real product")
    void remoteOrderStaffRedaction() {
        // The role is loaded from the database rather than constructed, so this exercises the
        // seeded V6 grants and restrictions rather than a test's idea of them. That is what makes
        // it the check that step 4's answer actually took effect.
        RoleView remoteStaff = roles.requireByName("REMOTE_ORDER_STAFF");
        assertThat(remoteStaff.canView(Section.PRODUCTS)).isTrue();
        assertThat(remoteStaff.canSee(ProtectedField.PRODUCT_SUPPLIER)).isFalse();

        SupplierView supplier = suppliers.create(NewSupplier.domestic(
                "ProdIT — Hidden supplier", "EL066666004"));
        ProductView product = products.create(new NewProduct(
                "ProdIT-REDACT-01", "5209999900001", "ProdIT redacted item", ProductType.GOODS,
                pieceId(), standardRateId(), Money.ofEur("129.00"),
                supplier.id(), "HID-99"));

        ProductView asStaffSees = products.requireFor(product.id(), remoteStaff);

        assertThat(asStaffSees.supplier()).isEmpty();
        assertThat(asStaffSees.supplierSkuIfAny()).isEmpty();
        assertThat(asStaffSees.isRedacted()).isTrue();
        // What an order picker needs is untouched — the selling price above all.
        assertThat(asStaffSees.sellingPriceIfAny()).contains(Money.ofEur("129.00"));
        assertThat(asStaffSees.eanIfAny()).contains("5209999900001");

        // Unredacted for the core's own rules, which cannot cost a sale from a blanked field.
        assertThat(products.require(product.id()).supplier()).contains(supplier.id());

        assertThat(products.allFor(remoteStaff))
                .filteredOn(view -> view.sku().equals("ProdIT-REDACT-01"))
                .singleElement()
                .satisfies(view -> assertThat(view.supplier()).isEmpty());
    }

    @Test
    @DisplayName("the Owner sees everything, and a role without Products access is refused")
    void ownerSeesEverythingAndDeniedRoleIsRefused() {
        RoleView owner = roles.requireByName("OWNER");
        SupplierView supplier = suppliers.create(NewSupplier.domestic(
                "ProdIT — Visible supplier", "EL066666005"));
        ProductView product = products.create(new NewProduct(
                "ProdIT-OWNER-01", null, "ProdIT owner view", ProductType.GOODS,
                pieceId(), standardRateId(), Money.ofEur("55.00"),
                supplier.id(), "VIS-1"));

        assertThat(products.requireFor(product.id(), owner).supplier()).contains(supplier.id());
        assertThat(products.requireFor(product.id(), owner).isRedacted()).isFalse();

        // Refused rather than empty: "you may not see products" and "there are no products" are
        // different answers, and an empty list cannot express the difference. A new role starts
        // with access to nothing, which is default-deny doing its job.
        RoleView noProducts = roles.create(
                new NewRole("PRODIT_NO_PRODUCTS", "Cannot see products"));
        roles.grant(noProducts.id(), Section.CUSTOMERS, AccessLevel.FULL);
        RoleView withoutProducts = roles.require(noProducts.id());

        assertThat(withoutProducts.canView(Section.PRODUCTS)).isFalse();
        assertThatExceptionOfType(SectionAccessDeniedException.class)
                .isThrownBy(() -> products.allFor(withoutProducts));
        assertThatExceptionOfType(SectionAccessDeniedException.class)
                .isThrownBy(() -> products.requireFor(product.id(), withoutProducts));
    }

    // ---------------------------------------------------------------------------------------
    // VAT class — the product level of the precedence rule
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a product's default VAT class is required and feeds the precedence rule")
    void defaultVatClassIsRequired() {
        VatClassView reduced = vatClasses.requireByCode("1131");
        ProductView product = products.create(NewProduct.goods(
                "ProdIT-VAT-01", "ProdIT reduced-rate item", pieceId(), reduced.id(), Money.ofEur("12.00")));

        assertThat(product.defaultVatClassId()).isEqualTo(reduced.id());
        // Bottom of the precedence chain, and the only level present here.
        assertThat(VatClassPrecedence.resolve(null, null, product.defaultVatClassId()).source())
                .isEqualTo(VatClassSource.PRODUCT);

        // NOT NULL in the schema: there is no fallback rate anywhere, so a product without a class
        // would be one that cannot be invoiced.
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'product'
                  AND column_name = 'default_vat_class_id'
                """, String.class))
                .isEqualTo("NO");

        assertThatExceptionOfType(InvalidProductException.class)
                .isThrownBy(() -> products.create(NewProduct.goods(
                        "ProdIT-VAT-02", "ProdIT unknown rate", pieceId(), 999_999L, null)))
                .withMessageContaining("No VAT class with id 999999");
    }

    // ---------------------------------------------------------------------------------------
    // Identity: SKU and barcode
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("SKU and barcode are both unique, and a blank scan matches nothing")
    void skuAndEanAreUnique() {
        products.create(new NewProduct(
                "ProdIT-UNIQ-01", "5209999900002", "ProdIT unique one", ProductType.GOODS,
                pieceId(), standardRateId(), null, null, null));

        assertThatExceptionOfType(InvalidProductException.class)
                .isThrownBy(() -> products.create(NewProduct.goods(
                        "prodit-uniq-01", "ProdIT duplicate SKU", pieceId(), standardRateId(), null)))
                .withMessageContaining("already exists");

        assertThatExceptionOfType(InvalidProductException.class)
                .isThrownBy(() -> products.create(new NewProduct(
                        "ProdIT-UNIQ-02", "5209999900002", "ProdIT duplicate barcode",
                        ProductType.GOODS, pieceId(), standardRateId(),
                        null, null, null)))
                .withMessageContaining("scan ambiguous");

        assertThat(products.findByEan("5209999900002")).isPresent();
        // A misread must not become a confidently wrong product on an invoice.
        assertThat(products.findByEan(null)).isEmpty();
        assertThat(products.findByEan("  ")).isEmpty();
    }

    @Test
    @DisplayName("a service product carries no stock, and says so")
    void servicesAreNotStocked() {
        ProductView repair = products.create(NewProduct.service(
                "ProdIT-SVC-01", "ProdIT machine service", pieceId(), standardRateId(),
                Money.ofEur("60.00")));

        assertThat(repair.type()).isEqualTo(ProductType.SERVICE);
        assertThat(repair.isStocked())
                .as("a service has no lots, credits Services, and costs against Cost of service "
                        + "sold — three real differences, not a label")
                .isFalse();
    }

    @Test
    @DisplayName("a product is deactivated, never deleted")
    void deactivateAndReactivate() {
        ProductView product = products.create(NewProduct.goods(
                "ProdIT-DISC-01", "ProdIT discontinued", pieceId(), standardRateId(), Money.ofEur("5.00")));

        products.deactivate(product.id());
        assertThat(products.require(product.id()).active()).isFalse();
        assertThat(products.active()).extracting(ProductView::sku)
                .doesNotContain("ProdIT-DISC-01");
        assertThat(products.all()).extracting(ProductView::sku).contains("ProdIT-DISC-01");

        products.reactivate(product.id());
        assertThat(products.require(product.id()).active()).isTrue();
    }

    @Test
    @DisplayName("a missing product names what it was asked for")
    void missingProduct() {
        assertThatExceptionOfType(ProductNotFoundException.class)
                .isThrownBy(() -> products.require(999_999L))
                .withMessageContaining("999999");

        assertThatExceptionOfType(ProductNotFoundException.class)
                .isThrownBy(() -> products.requireBySku("ProdIT-NOT-A-SKU"))
                .withMessageContaining("ProdIT-NOT-A-SKU");
    }

    @Test
    @DisplayName("no bundle flag exists while Q11 is open")
    void noBundleFlagYet() {
        // Bundles are in brief §5 but were left out of the agreed Phase 1 scope. A flag nothing
        // honours reads as a half-built feature to whoever finds it next.
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'product'
                """, String.class))
                .noneSatisfy(column -> assertThat(column.toLowerCase())
                        .containsAnyOf("bundle", "composite", "kit"));
    }
}
