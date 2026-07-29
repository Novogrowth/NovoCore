package gr.novotrade.novocore.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.bundle.BundleComponentLine;
import gr.novotrade.novocore.core.api.bundle.BundleComponentView;
import gr.novotrade.novocore.core.api.bundle.BundleDecomposition;
import gr.novotrade.novocore.core.api.bundle.BundleNotDecomposableException;
import gr.novotrade.novocore.core.api.bundle.BundleService;
import gr.novotrade.novocore.core.api.bundle.InvalidBundleException;
import gr.novotrade.novocore.core.api.bundle.NewBundleComponent;
import gr.novotrade.novocore.core.api.inventory.InvalidInventoryLotException;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewInventoryLot;
import gr.novotrade.novocore.core.api.inventory.StockLevels;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.inventory.StockNotApplicableException;
import gr.novotrade.novocore.core.api.product.InvalidProductException;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.ProductType;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.SectionAccessDeniedException;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.supplier.SupplierView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bundle and composite products — Q11, answered "build now", against brief §5 in full.
 *
 * <p>The two properties worth the most here are that a bundle holds no stock of its own, and that a
 * decomposition's component lines are the <em>same money</em> as its bundle line. Everything else is a
 * guard around one of those.
 */
class BundleIT extends AbstractCoreIntegrationTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);

    @Autowired
    private RoleService roles;

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private BundleService bundles;

    @Autowired
    private InventoryService inventory;

    @Autowired
    private ProductService products;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private UnitOfMeasureService unitsOfMeasure;

    @Autowired
    private JdbcTemplate jdbc;

    private long standardRateId() {
        return vatClasses.requireByCode("1410").id();
    }

    private long pieceId() {
        return unitsOfMeasure.requireByCode("PIECE").id();
    }

    private long kilogramId() {
        return unitsOfMeasure.requireByCode("KILOGRAM").id();
    }

    private ProductView goods(String sku, String price) {
        return products.create(NewProduct.goods(
                sku, sku + " item", pieceId(), standardRateId(),
                price == null ? null : Money.ofEur(price)));
    }

    private ProductView coffee(String sku, String price) {
        return products.create(NewProduct.goods(
                sku, sku + " coffee", kilogramId(), standardRateId(), Money.ofEur(price)));
    }

    private ProductView service(String sku, String price) {
        return products.create(NewProduct.service(
                sku, sku + " service", pieceId(), standardRateId(), Money.ofEur(price)));
    }

    private void receive(ProductView product, long quantity, StockLocation location) {
        inventory.receive(NewInventoryLot.pooled(
                product.id(), Quantity.of(quantity), UnitCost.ofEur("10.00"), MARCH, location));
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("defining a bundle")
    class Defining {

        @Test
        @DisplayName("a bundle is a product with its own SKU and a component list")
        void defineAndRead() {
            ProductView grinder = goods("BunIT-GRIND-01", "189.00");
            ProductView tamper = goods("BunIT-TAMP-01", "19.90");
            ProductView giftSet = goods("BunIT-SET-01", "199.00");

            List<BundleComponentView> components = bundles.define(giftSet.id(), List.of(
                    NewBundleComponent.one(grinder.id()),
                    NewBundleComponent.of(tamper.id(), 2)));

            assertThat(components).extracting(BundleComponentView::componentSku)
                    .containsExactly("BunIT-GRIND-01", "BunIT-TAMP-01");
            assertThat(components).extracting(BundleComponentView::quantityPerBundle)
                    .containsExactly(Quantity.of(1L), Quantity.of(2L));
            assertThat(bundles.isBundle(giftSet.id())).isTrue();
            assertThat(products.require(giftSet.id()).isBundle()).isTrue();
            assertThat(bundles.allBundles()).extracting(ProductView::sku).contains("BunIT-SET-01");

            // The other direction, which is what a deactivation has to ask.
            assertThat(bundles.bundlesContaining(grinder.id()))
                    .extracting(BundleComponentView::bundleProductId)
                    .containsExactly(giftSet.id());
        }

        @Test
        @DisplayName("re-defining replaces the whole list rather than merging into it")
        void defineReplacesWholesale() {
            // A partial change would leave the rest of a bundle in a state nobody chose — the argument
            // that makes a chart-of-accounts reorder name every member.
            ProductView first = goods("BunIT-REP-01", "10.00");
            ProductView second = goods("BunIT-REP-02", "20.00");
            ProductView bundle = goods("BunIT-REP-SET", "25.00");

            bundles.define(bundle.id(), List.of(NewBundleComponent.one(first.id())));
            bundles.define(bundle.id(), List.of(NewBundleComponent.of(second.id(), 3)));

            assertThat(bundles.componentsOf(bundle.id()))
                    .extracting(BundleComponentView::componentSku)
                    .containsExactly("BunIT-REP-02");
        }

        @Test
        @DisplayName("dissolving stops it being a bundle and clears the components")
        void dissolve() {
            ProductView component = goods("BunIT-DIS-01", "10.00");
            ProductView bundle = goods("BunIT-DIS-SET", "15.00");
            bundles.define(bundle.id(), List.of(NewBundleComponent.one(component.id())));

            bundles.dissolve(bundle.id());

            assertThat(bundles.isBundle(bundle.id())).isFalse();
            assertThat(bundles.componentsOf(bundle.id())).isEmpty();
            assertThatExceptionOfType(InvalidBundleException.class)
                    .isThrownBy(() -> bundles.dissolve(bundle.id()))
                    .withMessageContaining("is not a bundle");
        }

        @Test
        @DisplayName("an empty component list is refused — that is not a bundle")
        void emptyListRefused() {
            ProductView bundle = goods("BunIT-EMPTY-01", "15.00");

            assertThatExceptionOfType(InvalidBundleException.class)
                    .isThrownBy(() -> bundles.define(bundle.id(), List.of()))
                    .withMessageContaining("at least one component");
            assertThat(products.require(bundle.id()).isBundle())
                    .as("the flag and the components are set in one transaction, so a failed define "
                            + "leaves neither")
                    .isFalse();
        }

        @Test
        @DisplayName("a bundle cannot contain itself, in code and in the database")
        void noSelfReference() {
            ProductView bundle = goods("BunIT-SELF-01", "15.00");

            assertThatExceptionOfType(InvalidBundleException.class)
                    .isThrownBy(() -> bundles.define(bundle.id(),
                            List.of(NewBundleComponent.one(bundle.id()))))
                    .withMessageContaining("cannot contain itself");

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO bundle_component (bundle_product_id, component_product_id,
                                                  quantity_per_bundle)
                    VALUES (?, ?, 1)
                    """, bundle.id(), bundle.id()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("bundle_component_not_itself");
        }

        @Test
        @DisplayName("a component may not itself be a bundle — one level deep, deliberately")
        void oneLevelDeep() {
            // Same rule as VatClass's island-reduced counterpart, and the same reason: it makes a cycle
            // impossible by construction and keeps allocation single-pass.
            ProductView leaf = goods("BunIT-NEST-01", "10.00");
            ProductView inner = goods("BunIT-NEST-SET-A", "18.00");
            ProductView outer = goods("BunIT-NEST-SET-B", "30.00");
            bundles.define(inner.id(), List.of(NewBundleComponent.of(leaf.id(), 2)));

            assertThatExceptionOfType(InvalidBundleException.class)
                    .isThrownBy(() -> bundles.define(outer.id(),
                            List.of(NewBundleComponent.one(inner.id()))))
                    .withMessageContaining("one level deep");
        }

        @Test
        @DisplayName("a component listed twice is refused, in code and by a unique index")
        void noDuplicateComponents() {
            ProductView component = goods("BunIT-DUP-01", "10.00");
            ProductView bundle = goods("BunIT-DUP-SET", "18.00");

            assertThatExceptionOfType(InvalidBundleException.class)
                    .isThrownBy(() -> bundles.define(bundle.id(), List.of(
                            NewBundleComponent.one(component.id()),
                            NewBundleComponent.of(component.id(), 2))))
                    .withMessageContaining("listed twice");

            bundles.define(bundle.id(), List.of(NewBundleComponent.one(component.id())));
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO bundle_component (bundle_product_id, component_product_id,
                                                  quantity_per_bundle)
                    VALUES (?, ?, 1)
                    """, bundle.id(), component.id()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("bundle_component_unique");
        }

        @Test
        @DisplayName("an inactive component is refused: the bundle could not be assembled")
        void inactiveComponentRefused() {
            ProductView component = goods("BunIT-INACT-01", "10.00");
            ProductView bundle = goods("BunIT-INACT-SET", "18.00");
            products.deactivate(component.id());

            assertThatExceptionOfType(InvalidBundleException.class)
                    .isThrownBy(() -> bundles.define(bundle.id(),
                            List.of(NewBundleComponent.one(component.id()))))
                    .withMessageContaining("inactive");
        }

        @Test
        @DisplayName("a component quantity honours the component's own unit of measure")
        void componentQuantityFollowsTheUnit() {
            // The V11 obligation again: 250 grams of coffee in a gift set is fine, two and a half
            // grinders is a typing mistake.
            ProductView grinder = goods("BunIT-UNIT-01", "189.00");
            ProductView beans = coffee("BunIT-UNIT-02", "24.50");
            ProductView bundle = goods("BunIT-UNIT-SET", "199.00");

            assertThatExceptionOfType(InvalidBundleException.class)
                    .isThrownBy(() -> bundles.define(bundle.id(), List.of(
                            new NewBundleComponent(grinder.id(), Quantity.of("1.5")))))
                    .withMessageContaining("has a fraction");

            List<BundleComponentView> components = bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(grinder.id()),
                    new NewBundleComponent(beans.id(), Quantity.of("0.250"))));

            assertThat(components).extracting(BundleComponentView::quantityPerBundle)
                    .contains(Quantity.of("0.25"));
        }

        @Test
        @DisplayName("a zero or negative component quantity is refused by the database too")
        void componentQuantityIsPositive() {
            ProductView component = goods("BunIT-ZERO-01", "10.00");
            ProductView bundle = goods("BunIT-ZERO-SET", "18.00");

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO bundle_component (bundle_product_id, component_product_id,
                                                  quantity_per_bundle)
                    VALUES (?, ?, 0)
                    """, bundle.id(), component.id()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("bundle_component_quantity_positive");
        }
    }

    @Nested
    @DisplayName("a bundle has no stock of its own (brief §5)")
    class NoStockOfItsOwn {

        @Test
        @DisplayName("availability is the minimum over components, in whole bundles")
        void availabilityFromComponents() {
            ProductView grinder = goods("BunIT-AVAIL-01", "189.00");
            ProductView tamper = goods("BunIT-AVAIL-02", "19.90");
            ProductView bundle = goods("BunIT-AVAIL-SET", "199.00");
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(grinder.id()),
                    NewBundleComponent.of(tamper.id(), 2)));

            receive(grinder, 5, StockLocation.INVENTORY);
            receive(tamper, 7, StockLocation.INVENTORY);

            StockLevels levels = inventory.stockOf(bundle.id());

            // Five grinders would allow five sets; seven tampers at two each allow three. The tamper is
            // the constraint, and half a tamper is not half a bundle.
            assertThat(levels.at(StockLocation.INVENTORY)).isEqualTo(Quantity.of(3L));
            assertThat(levels.sellable()).isEqualTo(Quantity.of(3L));
        }

        @Test
        @DisplayName("a component's stock at an unsellable location does not make bundles sellable")
        void locationCarriesThrough() {
            ProductView grinder = goods("BunIT-LOC-01", "189.00");
            ProductView tamper = goods("BunIT-LOC-02", "19.90");
            ProductView bundle = goods("BunIT-LOC-SET", "199.00");
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(grinder.id()),
                    NewBundleComponent.one(tamper.id())));

            receive(grinder, 4, StockLocation.INVENTORY);
            receive(tamper, 4, StockLocation.DAMAGED_GOODS);

            // Q7 carried through the composition: four of each on hand, and not one sellable bundle.
            assertThat(inventory.stockOf(bundle.id()).sellable()).isEqualTo(Quantity.ZERO);
            assertThat(inventory.stockOf(bundle.id()).at(StockLocation.DAMAGED_GOODS))
                    .isEqualTo(Quantity.ZERO);
        }

        @Test
        @DisplayName("service components do not constrain availability")
        void serviceComponentsDoNotLimit() {
            // A machine sold with its installation. The installation has revenue allocated to it and
            // nothing to take off a shelf, so it must not cap the bundle at zero.
            ProductView machine = goods("BunIT-SVCCOMP-01", "899.00");
            ProductView installation = service("BunIT-SVCCOMP-02", "80.00");
            ProductView bundle = goods("BunIT-SVCCOMP-SET", "949.00");
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(machine.id()),
                    NewBundleComponent.one(installation.id())));

            receive(machine, 2, StockLocation.INVENTORY);

            assertThat(inventory.stockOf(bundle.id()).sellable()).isEqualTo(Quantity.of(2L));
        }

        @Test
        @DisplayName("a bundle of only services refuses to answer rather than answering zero")
        void serviceOnlyBundle() {
            ProductView first = service("BunIT-ALLSVC-01", "40.00");
            ProductView second = service("BunIT-ALLSVC-02", "60.00");
            ProductView bundle = products.create(NewProduct.service(
                    "BunIT-ALLSVC-SET", "BunIT service package", pieceId(), standardRateId(),
                    Money.ofEur("90.00")));
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(first.id()),
                    NewBundleComponent.one(second.id())));

            // Zero would say the opposite of what is true: this is available without limit.
            assertThatExceptionOfType(StockNotApplicableException.class)
                    .isThrownBy(() -> inventory.stockOf(bundle.id()))
                    .withMessageContaining("no stocked components");
        }

        @Test
        @DisplayName("a bundle cannot receive a lot, and a product with lots cannot become a bundle")
        void bundlesAndLotsAreMutuallyExclusive() {
            ProductView component = goods("BunIT-LOT-01", "10.00");
            ProductView bundle = goods("BunIT-LOT-SET", "18.00");
            bundles.define(bundle.id(), List.of(NewBundleComponent.one(component.id())));

            // Otherwise the same goods would be counted twice, once as the bundle and once as its parts.
            assertThatExceptionOfType(InvalidInventoryLotException.class)
                    .isThrownBy(() -> inventory.receive(NewInventoryLot.pooled(
                            bundle.id(), Quantity.of(1L), UnitCost.ofEur("10.00"), MARCH,
                            StockLocation.INVENTORY)))
                    .withMessageContaining("no stock of its own");

            ProductView stocked = goods("BunIT-LOT-02", "10.00");
            receive(stocked, 3, StockLocation.INVENTORY);
            assertThatExceptionOfType(InvalidBundleException.class)
                    .isThrownBy(() -> bundles.define(stocked.id(),
                            List.of(NewBundleComponent.one(component.id()))))
                    .withMessageContaining("counted twice");
        }

        @Test
        @DisplayName("a serial-tracked product cannot be a bundle, in code and in the database")
        void serialTrackedCannotBeABundle() {
            ProductView component = goods("BunIT-SER-01", "10.00");
            ProductView machine = products.create(NewProduct.serializedGoods(
                    "BunIT-SER-SET", "BunIT serialised", pieceId(), standardRateId(),
                    Money.ofEur("2400.00")));

            assertThatExceptionOfType(InvalidBundleException.class)
                    .isThrownBy(() -> bundles.define(machine.id(),
                            List.of(NewBundleComponent.one(component.id()))))
                    .withMessageContaining("serial-tracked");

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE product SET bundle = true WHERE id = ?", machine.id()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("product_bundle_is_not_serial_tracked");
        }

        @Test
        @DisplayName("deactivating a component an active bundle needs is refused, and names it")
        void componentCannotBeQuietlyDiscontinued() {
            ProductView component = goods("BunIT-DEACT-01", "10.00");
            ProductView bundle = goods("BunIT-DEACT-SET", "18.00");
            bundles.define(bundle.id(), List.of(NewBundleComponent.one(component.id())));

            assertThatExceptionOfType(InvalidProductException.class)
                    .isThrownBy(() -> products.deactivate(component.id()))
                    .withMessageContaining("BunIT-DEACT-SET");

            // Deactivating the bundle first releases the component, which is the honest order.
            products.deactivate(bundle.id());
            products.deactivate(component.id());
            assertThat(products.require(component.id()).active()).isFalse();
        }
    }

    @Nested
    @DisplayName("decomposition — brief §5's one core-level rule")
    class Decomposition {

        @Test
        @DisplayName("the component lines are the same money as the bundle line, to the cent")
        void componentLinesSumToTheBundleLine() {
            // Grinder 189.00, 1 kg of coffee 24.50, tamper 19.90 — 233.40 standalone, sold at 199.00.
            ProductView grinder = goods("BunIT-DEC-01", "189.00");
            ProductView beans = coffee("BunIT-DEC-02", "24.50");
            ProductView tamper = goods("BunIT-DEC-03", "19.90");
            ProductView bundle = goods("BunIT-DEC-SET", "199.00");
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(grinder.id()),
                    new NewBundleComponent(beans.id(), Quantity.of("1.000")),
                    NewBundleComponent.one(tamper.id())));

            assertThat(bundles.standaloneValueOf(bundle.id())).isEqualTo(Money.ofEur("233.40"));

            BundleDecomposition decomposition = bundles.decompose(
                    bundle.id(), Quantity.of(1L), Money.ofEur("199.00"));

            Money allocated = decomposition.componentLines().stream()
                    .map(BundleComponentLine::allocatedAmount)
                    .reduce(Money.zero(Money.EUR), Money::plus);
            assertThat(allocated)
                    .as("this is the invariant BundleDecomposition enforces in its constructor")
                    .isEqualTo(Money.ofEur("199.00"));
            assertThat(decomposition.componentLines()).hasSize(3);
            // Every component line knows which bundle it came from — brief §5's "linked, not
            // duplicated", which is what lets a report roll up either level without double-counting.
            assertThat(decomposition.componentLines())
                    .allSatisfy(line -> assertThat(line.bundleProductId()).isEqualTo(bundle.id()));
        }

        @Test
        @DisplayName("quantities multiply out, per component, for more than one bundle")
        void quantitiesMultiply() {
            ProductView grinder = goods("BunIT-MULT-01", "189.00");
            ProductView beans = coffee("BunIT-MULT-02", "24.50");
            ProductView bundle = goods("BunIT-MULT-SET", "199.00");
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.of(grinder.id(), 1),
                    new NewBundleComponent(beans.id(), Quantity.of("0.250"))));

            BundleDecomposition decomposition = bundles.decompose(
                    bundle.id(), Quantity.of(4L), Money.ofEur("760.00"));

            assertThat(decomposition.componentLines())
                    .filteredOn(line -> line.componentSku().equals("BunIT-MULT-01"))
                    .singleElement()
                    .satisfies(line -> assertThat(line.quantity()).isEqualTo(Quantity.of(4L)));
            assertThat(decomposition.componentLines())
                    .filteredOn(line -> line.componentSku().equals("BunIT-MULT-02"))
                    .singleElement()
                    .satisfies(line -> assertThat(line.quantity()).isEqualTo(Quantity.of("1.0")));
        }

        @Test
        @DisplayName("only stocked lines consume stock; a bundled service still takes revenue")
        void stockedLinesAreDistinguished() {
            ProductView machine = goods("BunIT-STK-01", "899.00");
            ProductView installation = service("BunIT-STK-02", "80.00");
            ProductView bundle = goods("BunIT-STK-SET", "949.00");
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(machine.id()),
                    NewBundleComponent.one(installation.id())));

            BundleDecomposition decomposition = bundles.decompose(
                    bundle.id(), Quantity.of(1L), Money.ofEur("949.00"));

            assertThat(decomposition.stockedLines()).hasSize(1);
            assertThat(decomposition.componentLines())
                    .filteredOn(line -> !line.stocked())
                    .singleElement()
                    .satisfies(line -> assertThat(line.allocatedAmount().isPositive()).isTrue());
        }

        @Test
        @DisplayName("a return decomposes as the negative of the sale, part for part")
        void returnMirrorsTheSale() {
            ProductView first = goods("BunIT-RET-01", "77.00");
            ProductView second = goods("BunIT-RET-02", "13.00");
            ProductView bundle = goods("BunIT-RET-SET", "80.00");
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(first.id()),
                    NewBundleComponent.one(second.id())));

            BundleDecomposition sale = bundles.decompose(
                    bundle.id(), Quantity.of(1L), Money.ofEur("80.00"));
            BundleDecomposition credit = bundles.decompose(
                    bundle.id(), Quantity.of(1L), Money.ofEur("-80.00"));

            // Otherwise a returned bundle leaves a per-component residual that never clears.
            for (int i = 0; i < sale.componentLines().size(); i++) {
                assertThat(credit.componentLines().get(i).allocatedAmount())
                        .isEqualTo(sale.componentLines().get(i).allocatedAmount().negated());
            }
        }

        @Test
        @DisplayName("an unpriced component refuses decomposition rather than weighing zero")
        void unpricedComponentRefuses() {
            // A zero weight would push the whole bundle's revenue onto the priced components and report
            // this one as pure margin. Same stance as having no fallback VAT rate.
            ProductView priced = goods("BunIT-UNPR-01", "50.00");
            ProductView unpriced = goods("BunIT-UNPR-02", null);
            ProductView bundle = goods("BunIT-UNPR-SET", "55.00");
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(priced.id()),
                    NewBundleComponent.one(unpriced.id())));

            assertThatExceptionOfType(BundleNotDecomposableException.class)
                    .isThrownBy(() -> bundles.decompose(
                            bundle.id(), Quantity.of(1L), Money.ofEur("55.00")))
                    .withMessageContaining("BunIT-UNPR-02")
                    .withMessageContaining("pure margin");

            // And it is findable before a sale rather than during one.
            assertThat(bundles.bundlesWithUnpricedComponents())
                    .extracting(ProductView::sku)
                    .contains("BunIT-UNPR-SET");

            products.changeSellingPrice(unpriced.id(), Money.ofEur("5.00"));
            assertThat(bundles.bundlesWithUnpricedComponents())
                    .extracting(ProductView::sku)
                    .doesNotContain("BunIT-UNPR-SET");
        }

        @Test
        @DisplayName("decomposing something that is not a bundle is refused")
        void notABundle() {
            ProductView plain = goods("BunIT-PLAIN-01", "10.00");

            assertThatExceptionOfType(InvalidBundleException.class)
                    .isThrownBy(() -> bundles.decompose(
                            plain.id(), Quantity.of(1L), Money.ofEur("10.00")))
                    .withMessageContaining("is not a bundle");
        }

        @Test
        @DisplayName("a non-positive bundle quantity is refused; a return is a negative total")
        void quantityMustBePositive() {
            ProductView component = goods("BunIT-QTY-01", "10.00");
            ProductView bundle = goods("BunIT-QTY-SET", "18.00");
            bundles.define(bundle.id(), List.of(NewBundleComponent.one(component.id())));

            assertThatExceptionOfType(InvalidBundleException.class)
                    .isThrownBy(() -> bundles.decompose(
                            bundle.id(), Quantity.ZERO, Money.ofEur("18.00")))
                    .withMessageContaining("not positive");
        }

        @Test
        @DisplayName("a discount lands proportionally, not equally")
        void discountIsProportional() {
            // The reason allocation cannot simply copy list prices: the bundle sells for less than the
            // sum of its parts, and the cheap component must not absorb the whole discount.
            ProductView expensive = goods("BunIT-PROP-01", "900.00");
            ProductView cheap = goods("BunIT-PROP-02", "100.00");
            ProductView bundle = goods("BunIT-PROP-SET", "900.00");
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(expensive.id()),
                    NewBundleComponent.one(cheap.id())));

            BundleDecomposition decomposition = bundles.decompose(
                    bundle.id(), Quantity.of(1L), Money.ofEur("900.00"));

            assertThat(decomposition.componentLines().get(0).allocatedAmount())
                    .isEqualTo(Money.ofEur("810.00"));
            assertThat(decomposition.componentLines().get(1).allocatedAmount())
                    .isEqualTo(Money.ofEur("90.00"));
        }
    }

    @Nested
    @DisplayName("redaction — a bundle is a product, so its lists carry the same restricted fields")
    class Redaction {

        /**
         * A role that hides a product's supplier, created here rather than taken from the seed.
         *
         * <p>These two tests used to use {@code REMOTE_ORDER_STAFF}, which V6 seeded with exactly
         * these restrictions. <strong>V26 removed them</strong> — the business has no
         * confidentiality need around a product's cost or supplier — and after that no role in real
         * data restricts anything, so a seeded role can no longer demonstrate redaction.
         *
         * <p>The tests are kept rather than deleted, because what they prove is not the policy but
         * the wiring: step 14c moved redaction out of the controller and into
         * {@code allBundlesFor} / {@code bundlesWithUnpricedComponentsFor} precisely so an
         * architecture rule could forbid the unredacted read from the web layer. If nothing
         * exercised that, a change reverting it would pass in silence.
         */
        private RoleView roleRestrictingSupplier() {
            RoleView role = roles.create(new NewRole(
                    "BUNDLEIT_RESTRICTED_" + System.nanoTime(), "Supplier hidden"));
            roles.grant(role.id(), Section.PRODUCTS, AccessLevel.VIEW);
            roles.restrictField(role.id(), ProtectedField.PRODUCT_SUPPLIER, true);
            return roles.require(role.id());
        }

        @Test
        @DisplayName("Remote/Order Staff sees no supplier on a bundle in allBundlesFor")
        void allBundlesForRedacts() {
            RoleView remoteStaff = roleRestrictingSupplier();
            SupplierView supplier = suppliers.create(NewSupplier.domestic(
                    "BundleIT — redaction supplier", "EL066777001"));
            ProductView component = products.create(NewProduct.goods(
                    "BUNDLEIT-REDACT-COMP", "Component", pieceId(), standardRateId(),
                    Money.ofEur("10.00")));
            ProductView bundle = products.create(new NewProduct(
                    "BUNDLEIT-REDACT-01", null, "Redacted bundle", ProductType.GOODS,
                    pieceId(), standardRateId(), Money.ofEur("18.00"),
                    supplier.id(), "SUPPLIER-BUNDLE-CODE", false));
            bundles.define(bundle.id(), List.of(NewBundleComponent.one(component.id())));

            ProductView asStaffSees = bundles.allBundlesFor(remoteStaff).stream()
                    .filter(view -> view.sku().equals("BUNDLEIT-REDACT-01"))
                    .findFirst()
                    .orElseThrow();

            // The behaviour was already right when the controller redacted by hand. What changed in
            // step 14c is where it lives: in the service, so the architecture rule can forbid the
            // unredacted read from the web layer at all.
            assertThat(asStaffSees.supplier()).isEmpty();
            assertThat(asStaffSees.supplierSkuIfAny()).isEmpty();
            assertThat(asStaffSees.isRedacted()).isTrue();
            // What an order picker needs is untouched.
            assertThat(asStaffSees.sellingPriceIfAny()).contains(Money.ofEur("18.00"));

            // Unredacted for the core's own rules.
            assertThat(bundles.allBundles().stream()
                    .filter(view -> view.sku().equals("BUNDLEIT-REDACT-01"))
                    .findFirst()
                    .orElseThrow()
                    .supplier())
                    .contains(supplier.id());
        }

        @Test
        @DisplayName("bundlesWithUnpricedComponentsFor redacts too")
        void unpricedListRedacts() {
            RoleView remoteStaff = roleRestrictingSupplier();
            SupplierView supplier = suppliers.create(NewSupplier.domestic(
                    "BundleIT — unpriced supplier", "EL066777002"));
            ProductView unpriced = products.create(NewProduct.goods(
                    "BUNDLEIT-REDACT-UNPRICED-COMP", "Unpriced component", pieceId(),
                    standardRateId(), null));
            ProductView bundle = products.create(new NewProduct(
                    "BUNDLEIT-REDACT-02", null, "Bundle with unpriced part", ProductType.GOODS,
                    pieceId(), standardRateId(), Money.ofEur("30.00"),
                    supplier.id(), "SUPPLIER-UNPRICED-CODE", false));
            bundles.define(bundle.id(), List.of(NewBundleComponent.one(unpriced.id())));

            assertThat(bundles.bundlesWithUnpricedComponentsFor(remoteStaff))
                    .filteredOn(view -> view.sku().equals("BUNDLEIT-REDACT-02"))
                    .singleElement()
                    .satisfies(view -> {
                        assertThat(view.supplier()).isEmpty();
                        assertThat(view.supplierSkuIfAny()).isEmpty();
                    });
        }

        @Test
        @DisplayName("a role that cannot view Products is refused, not given an empty list")
        void aRoleWithoutProductsIsRefused() {
            RoleView noProducts = roles.create(
                    new NewRole("BUNDLEIT_NO_PRODUCTS", "Cannot see products"));
            roles.grant(noProducts.id(), Section.CUSTOMERS, AccessLevel.FULL);
            RoleView withoutProducts = roles.require(noProducts.id());

            // "You may not see this" and "there are none" are different answers, and an empty list
            // cannot express the difference — the same contract ProductService.allFor states.
            assertThatExceptionOfType(SectionAccessDeniedException.class)
                    .isThrownBy(() -> bundles.allBundlesFor(withoutProducts));
            assertThatExceptionOfType(SectionAccessDeniedException.class)
                    .isThrownBy(() -> bundles.bundlesWithUnpricedComponentsFor(withoutProducts));
        }
    }

}
