package gr.novotrade.novocore.core.api.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Field-level redaction — <strong>the test that discharges step 4's outstanding obligation.</strong>
 *
 * <p>{@code ProtectedField}'s three entries have been live configuration since step 4, seeded
 * against Remote/Order Staff and enforced by {@link RoleView}, with nothing to guard because
 * products did not exist. This is where that either takes effect or silently does not.
 *
 * <p>No database. {@link ProductView} is a record and {@link RoleView} is pure logic, so every case
 * — including a last purchase price, which cannot exist in real data until inventory lots arrive in
 * step 6 — can be constructed directly. That matters: waiting for step 6 to test the redaction of
 * the cost field would mean shipping the mechanism untested for a whole step.
 */
class ProductViewTest {

    private static final Money PRICE = Money.ofEur("42.50");
    /**
     * A {@code UnitCost} rather than a {@code Money} since step 6: it comes from a lot's unit cost, so
     * it carries six decimals. The trailing digits are the point — a cost that has had landed costs
     * allocated into it is not a round number of cents, and rounding it for display would show a figure
     * nobody paid.
     */
    private static final UnitCost LAST_PURCHASE_PRICE = UnitCost.ofEur("27.303333");

    /**
     * Units as they arrive from the lookup table (Q34), with no myDATA code — which is the state
     * every seeded unit is actually in until the verified AADE list is supplied.
     */
    private static final UnitOfMeasureView PIECE =
            new UnitOfMeasureView(1L, "PIECE", "Piece", false, null, true);
    private static final UnitOfMeasureView KILOGRAM =
            new UnitOfMeasureView(4L, "KILOGRAM", "Kilogram", true, null, true);

    /** Exactly the role V6 seeds: Products view-only, the three cost/supplier fields hidden. */
    private static RoleView remoteOrderStaff() {
        return new RoleView(3L, "REMOTE_ORDER_STAFF", "Home-based order staff", false, false, true,
                Map.of(
                        Section.SALES_ORDER_FULFILLMENT, AccessLevel.FULL,
                        Section.CUSTOMERS, AccessLevel.FULL,
                        Section.BACK_IN_STOCK_REMINDERS, AccessLevel.FULL,
                        Section.PRODUCTS, AccessLevel.VIEW),
                Set.of(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE,
                        ProtectedField.PRODUCT_SUPPLIER,
                        ProtectedField.PRODUCT_SUPPLIER_SKU));
    }

    private static RoleView owner() {
        return new RoleView(1L, "OWNER", "Everything", true, true, true, Map.of(), Set.of());
    }

    /** A product with every restricted field populated, so redaction has something to remove. */
    private static ProductView fullyPopulated() {
        return new ProductView(
                7L, "JJ-ESP-001", "5201234567890", "Espresso machine", "Rocket Espresso",
                ProductType.GOODS, PIECE, 9L,
                PRICE, 4L, "SUP-ESP-77", false, false, LAST_PURCHASE_PRICE, true, Set.of());
    }

    @Nested
    @DisplayName("Remote/Order Staff — the concrete case Q21 answered")
    class RemoteOrderStaffRedaction {

        @Test
        @DisplayName("cost and supplier are blanked, and are reported as hidden rather than unset")
        void hidesCostAndSupplier() {
            ProductView redacted = fullyPopulated().redactedFor(remoteOrderStaff());

            assertThat(redacted.supplier()).isEmpty();
            assertThat(redacted.supplierSkuIfAny()).isEmpty();
            assertThat(redacted.lastPurchasePriceIfAny()).isEmpty();

            // Blanked, not merely absent. A caller that cannot tell the difference would render
            // "no supplier" for a product that has one — the same confusion Section.isAvailable()
            // exists to prevent one level up.
            assertThat(redacted.isRedacted()).isTrue();
            assertThat(redacted.hiddenFields()).containsExactlyInAnyOrder(
                    ProtectedField.PRODUCT_LAST_PURCHASE_PRICE,
                    ProtectedField.PRODUCT_SUPPLIER,
                    ProtectedField.PRODUCT_SUPPLIER_SKU);
            assertThat(redacted.isHidden(ProtectedField.PRODUCT_SUPPLIER)).isTrue();
        }

        @Test
        @DisplayName("everything an order picker needs survives, including the selling price")
        void keepsWhatFulfilmentNeeds() {
            ProductView redacted = fullyPopulated().redactedFor(remoteOrderStaff());

            // The selling price is deliberately NOT a protected field: somebody packing an order
            // and answering the phone needs it. Only what a product cost us is hidden.
            assertThat(redacted.sellingPriceIfAny()).contains(PRICE);
            assertThat(redacted.sku()).isEqualTo("JJ-ESP-001");
            assertThat(redacted.name()).isEqualTo("Espresso machine");
            assertThat(redacted.eanIfAny()).contains("5201234567890");
            assertThat(redacted.unitOfMeasure()).isEqualTo(PIECE);
            assertThat(redacted.defaultVatClassId()).isEqualTo(9L);
            assertThat(redacted.id()).isEqualTo(7L);
            assertThat(redacted.active()).isTrue();
        }
    }

    @Nested
    @DisplayName("the rules redaction follows")
    class RedactionRules {

        @Test
        @DisplayName("a full-access role sees everything, and gets the same instance back")
        void fullAccessSeesEverything() {
            ProductView product = fullyPopulated();
            ProductView forOwner = product.redactedFor(owner());

            assertThat(forOwner).isSameAs(product);
            assertThat(forOwner.supplier()).contains(4L);
            assertThat(forOwner.lastPurchasePriceIfAny()).contains(LAST_PURCHASE_PRICE);
            assertThat(forOwner.isRedacted()).isFalse();
        }

        @Test
        @DisplayName("a role that cannot view products at all sees none of the fields either")
        void noSectionAccessMeansNoFields() {
            // Field restrictions narrow, never widen. This role has no PRODUCTS grant and no
            // recorded field restrictions, and must still not see the cost — otherwise "no
            // restriction recorded" would read as permission.
            RoleView noProducts = new RoleView(9L, "ACCOUNTS", null, false, false, true,
                    Map.of(Section.CHART_OF_ACCOUNTS, AccessLevel.FULL), Set.of());

            ProductView redacted = fullyPopulated().redactedFor(noProducts);

            assertThat(redacted.supplier()).isEmpty();
            assertThat(redacted.supplierSkuIfAny()).isEmpty();
            assertThat(redacted.lastPurchasePriceIfAny()).isEmpty();
        }

        @Test
        @DisplayName("an inactive role is redacted as if it granted nothing")
        void inactiveRoleSeesNothing() {
            RoleView suspended = new RoleView(4L, "SUSPENDED", null, false, false, false,
                    Map.of(Section.PRODUCTS, AccessLevel.FULL), Set.of());

            assertThat(fullyPopulated().redactedFor(suspended).supplier()).isEmpty();
        }

        @Test
        @DisplayName("hiding the supplier hides the supplier's SKU too, even if unrestricted")
        void hidingSupplierAlsoHidesSupplierSku() {
            // The narrowing rule worth being explicit about. A supplier code identifies the
            // supplier indirectly — that is what PRODUCT_SUPPLIER_SKU exists for — so returning
            // "SUP-ESP-77" while blanking the supplier would hand over the answer in a different
            // column. Narrowing is always the safe direction.
            RoleView supplierHiddenOnly = new RoleView(5L, "PARTIAL", null, false, false, true,
                    Map.of(Section.PRODUCTS, AccessLevel.VIEW),
                    Set.of(ProtectedField.PRODUCT_SUPPLIER));

            ProductView redacted = fullyPopulated().redactedFor(supplierHiddenOnly);

            assertThat(redacted.supplier()).isEmpty();
            assertThat(redacted.supplierSkuIfAny())
                    .as("the supplier's own code would identify the supplier anyway")
                    .isEmpty();
            assertThat(redacted.isHidden(ProtectedField.PRODUCT_SUPPLIER_SKU)).isTrue();
            // The cost was not restricted for this role, so it stays.
            assertThat(redacted.lastPurchasePriceIfAny()).contains(LAST_PURCHASE_PRICE);
        }

        @Test
        @DisplayName("hiding only the supplier's SKU leaves the supplier itself visible")
        void hidingOnlySupplierSku() {
            // The implication runs one way only. Hiding the code does not imply hiding the
            // supplier, and quietly widening the restriction would remove a field the role was
            // meant to keep.
            RoleView skuHiddenOnly = new RoleView(6L, "PARTIAL_SKU", null, false, false, true,
                    Map.of(Section.PRODUCTS, AccessLevel.VIEW),
                    Set.of(ProtectedField.PRODUCT_SUPPLIER_SKU));

            ProductView redacted = fullyPopulated().redactedFor(skuHiddenOnly);

            assertThat(redacted.supplier()).contains(4L);
            assertThat(redacted.supplierSkuIfAny()).isEmpty();
        }

        @Test
        @DisplayName("redaction is idempotent, so a re-redacted view does not lose more")
        void redactionIsIdempotent() {
            ProductView once = fullyPopulated().redactedFor(remoteOrderStaff());
            ProductView twice = once.redactedFor(remoteOrderStaff());

            assertThat(twice.hiddenFields()).isEqualTo(once.hiddenFields());
            assertThat(twice.sellingPriceIfAny()).contains(PRICE);
        }

        @Test
        @DisplayName("redacting a view whose supplier is genuinely unset does not fail")
        void redactingAnUnsuppliedProduct() {
            // The invariant "supplier SKU needs a supplier" must not fire on a redacted view,
            // where the supplier is null because it was blanked rather than because none exists.
            ProductView noSupplier = new ProductView(
                    8L, "JJ-BLEND-01", null, "House blend 250g", null,
                    ProductType.GOODS, KILOGRAM, 9L,
                    Money.ofEur("9.90"), null, null, false, false, null, true, Set.of());

            ProductView redacted = noSupplier.redactedFor(remoteOrderStaff());

            assertThat(redacted.supplier()).isEmpty();
            assertThat(redacted.isHidden(ProtectedField.PRODUCT_SUPPLIER)).isTrue();
        }
    }

    @Nested
    @DisplayName("Q5's invariant: a supplier SKU means nothing without a supplier")
    class SupplierPairInvariant {

        @Test
        @DisplayName("a supplier SKU with no supplier is refused on an unredacted view")
        void supplierSkuWithoutSupplierIsRefused() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new ProductView(
                            9L, "JJ-X", null, "Orphan supplier code", null,
                            ProductType.GOODS, PIECE, 9L,
                            null, null, "SUP-ORPHAN", false, false, null, true, Set.of()))
                    .withMessageContaining("supplier SKU but no supplier");
        }

        @Test
        @DisplayName("a supplier with no supplier SKU is perfectly ordinary")
        void supplierWithoutSupplierSkuIsFine() {
            ProductView product = new ProductView(
                    10L, "JJ-Y", null, "Bought under our own reference", null,
                    ProductType.GOODS, PIECE, 9L,
                    null, 4L, null, false, false, null, true, Set.of());

            assertThat(product.supplier()).contains(4L);
            assertThat(product.supplierSkuIfAny()).isEmpty();
        }
    }

    @Test
    @DisplayName("a service product is not stocked, and goods are")
    void stockedFollowsType() {
        assertThat(fullyPopulated().isStocked()).isTrue();

        ProductView repair = new ProductView(
                11L, "JJ-SVC-REPAIR", null, "Machine service", null,
                ProductType.SERVICE, PIECE, 9L,
                Money.ofEur("60.00"), null, null, false, false, null, true, Set.of());

        assertThat(repair.isStocked()).isFalse();
    }

    @Nested
    @DisplayName("the two step 6 flags")
    class InventoryFlags {

        @Test
        @DisplayName("a bundle is GOODS and still not stocked — it has no stock of its own")
        void aBundleIsNotStocked() {
            // Brief §5. The trap this guards: a bundle is typed GOODS, so isStocked() reading only the
            // type would report it as carrying stock, and something would try to receive a lot against
            // it — counting the same goods twice, once as the bundle and once as its parts.
            ProductView giftSet = new ProductView(
                    12L, "JJ-GIFT-01", null, "Brewing gift set", null,
                    ProductType.GOODS, PIECE, 9L,
                    Money.ofEur("129.00"), null, null, false, true, null, true, Set.of());

            assertThat(giftSet.type()).isEqualTo(ProductType.GOODS);
            assertThat(giftSet.isBundle()).isTrue();
            assertThat(giftSet.isStocked())
                    .as("a bundle's availability is computed from its components, so it holds none "
                            + "itself")
                    .isFalse();
        }

        @Test
        @DisplayName("the brand survives redaction — an order picker needs to know what a thing is")
        void brandIsNotRedacted() {
            // Deliberate, and the same reasoning as the selling price: what is kept from
            // Remote/Order Staff is what a product COST us and who supplies it. The brand is part
            // of what the product is, which is exactly what somebody picking an order needs.
            ProductView redacted = fullyPopulated().redactedFor(remoteOrderStaff());

            assertThat(redacted.brandIfAny()).contains("Rocket Espresso");
            assertThat(redacted.isRedacted())
                    .as("something WAS redacted, so this is not passing by redacting nothing")
                    .isTrue();
        }

        @Test
        @DisplayName("serial tracking survives redaction — it is not a restricted field")
        void serialTrackingIsNotRedacted() {
            // Deliberate: whether a machine is identified by serial number is something an order
            // picker has to know in order to pick the right one. What is hidden is what it cost.
            ProductView machine = new ProductView(
                    13L, "JJ-ESP-900", null, "Serialised espresso machine", null,
                    ProductType.GOODS, PIECE, 9L,
                    Money.ofEur("2400.00"), 4L, "SUP-900", true, false, LAST_PURCHASE_PRICE, true,
                    Set.of());

            ProductView redacted = machine.redactedFor(remoteOrderStaff());

            assertThat(redacted.isSerialTracked()).isTrue();
            assertThat(redacted.isStocked()).isTrue();
            assertThat(redacted.lastPurchasePriceIfAny()).isEmpty();
        }
    }
}
