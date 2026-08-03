package gr.novotrade.novocore.core.api.product;

import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.tax.VatClassPrecedence;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One product, as everything outside the core sees it.
 *
 * <p><strong>No external system reference ids.</strong> No Prosvasis Go product id, no WooCommerce
 * id — {@code CLAUDE.md} rule 2. The core knows its own SKU and its own id, and each adapter keeps
 * its own mapping table.
 *
 * <p><strong>This is the type that discharges step 4's field-restriction obligation.</strong> Three
 * {@link ProtectedField} entries were seeded and enforced by the permission model with nothing to
 * guard, because products did not exist. They exist now, and {@link #redactedFor(RoleView)} is the
 * one implementation of the redaction — one place rather than one per caller, so a second reader of
 * products cannot forget half of it.
 *
 * <p><strong>Stock is deliberately not here.</strong> It is derived from lots (brief §5 says it is
 * never stored) and it is not one number anyway — the location lives with the quantity and
 * sellability depends on stock at a sellable location. Q7 answered the shape:
 * {@code InventoryService.stockOf} returns {@code StockLevels}, which is per location plus a computed
 * sellable figure. Putting a single number on this record would have to pick one of those to be wrong
 * about.
 *
 * @param sku NovoCore's own SKU, unique. The handle everything else uses.
 * @param ean the barcode, unique when present. Null for products that have none — own-blend coffee
 *     bagged in-store, services.
 * @param defaultVatClassId the product level of {@link VatClassPrecedence} — the bottom of "invoice
 *     line beats customer beats product". Required, not nullable: there is deliberately no fallback
 *     rate anywhere in the system, so a product with no VAT class is a product that cannot be
 *     invoiced, and discovering that at invoicing time is worse than at creation time.
 * @param sellingPrice the regular selling price, or null where none has been set yet. Deliberately
 *     <em>not</em> a protected field: an order picker needs it. A null price must be refused at
 *     invoicing rather than treated as zero — a zero price is a silently free sale.
 * @param supplierId the single supplier this product comes from, or null (Q5: one product, one
 *     supplier, a plain reference and no many-to-many). Protected by
 *     {@link ProtectedField#PRODUCT_SUPPLIER}.
 * @param supplierSku the supplier's own code for this product. Meaningless without knowing which
 *     supplier, which is what Q5 was about, so the schema refuses one without the other. Protected
 *     by {@link ProtectedField#PRODUCT_SUPPLIER_SKU}.
 * @param serialTracked whether stock of this product is identified individually by serial number
 *     (brief §5's serialized products — coffee machines). Decides the shape of its lots: a
 *     serial-tracked lot holds units rather than a pooled quantity, and its write-offs use the
 *     specific unit's own cost instead of FIFO. Only a stocked, non-bundle product can be one.
 * @param brand the manufacturer or brand, or null. Not a protected field: an order picker needs to
 *     know what a product is, and the brand is part of what it is. Searchable alongside SKU, title
 *     and barcode.
 * @param bundle whether this is a bundle/composite product (Q11, brief §5). A bundle has its own SKU
 *     and <em>no stock of its own</em> — its availability is computed from its components, and it
 *     cannot receive a lot.
 * @param lastPurchasePrice the unit cost of the most recent lot — derived, never stored (Q6,
 *     consistent with stock). A {@code UnitCost} rather than a {@code Money} because that is what a
 *     lot carries: six decimals, since a cost that has had landed costs allocated into it is not a
 *     round number of cents, and rounding it for display would show a figure nobody paid. Empty for a
 *     product that has never been received. Protected by
 *     {@link ProtectedField#PRODUCT_LAST_PURCHASE_PRICE}.
 * @param hiddenFields the protected fields blanked out of this instance. Empty on an unredacted
 *     view. Carried so a caller can tell "hidden from you" from "not set" — two states that look
 *     identical on a screen and have entirely different fixes, the same distinction
 *     {@link Section#isAvailable()} draws one level up.
 */
public record ProductView(
        long id,
        @Mandatory String sku,
        String ean,
        @Mandatory String name,
        String brand,
        @Mandatory ProductType type,
        @Mandatory UnitOfMeasureView unitOfMeasure,
        long defaultVatClassId,
        Money sellingPrice,
        Long supplierId,
        String supplierSku,
        boolean serialTracked,
        boolean bundle,
        UnitCost lastPurchasePrice,
        boolean active,
        @Mandatory Set<ProtectedField> hiddenFields) {

    public ProductView {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        Objects.requireNonNull(hiddenFields, "hiddenFields");

        Set<ProtectedField> hidden = EnumSet.noneOf(ProtectedField.class);
        hidden.addAll(hiddenFields);
        hiddenFields = Collections.unmodifiableSet(hidden);

        // Q5's invariant: a supplier SKU with no supplier is the meaningless field that started
        // that question. Checked only when the supplier is visible — on a redacted view the
        // supplier is null because it was blanked, not because none is recorded, and throwing
        // there would turn a permission rule into a crash.
        if (supplierSku != null && supplierId == null
                && !hiddenFields.contains(ProtectedField.PRODUCT_SUPPLIER)) {
            throw new IllegalArgumentException(
                    "Product '" + sku + "' has a supplier SKU but no supplier. The supplier's code "
                            + "identifies nothing without knowing whose code it is.");
        }
    }

    /**
     * This product as the given role may see it, with restricted fields blanked out.
     *
     * <p>Discharges the step 4 obligation. Delegates every decision to
     * {@link RoleView#canSee(ProtectedField)} rather than re-implementing it, so section access is
     * checked first and a role that cannot view Products at all does not see a product's cost even
     * with no restriction recorded against that field.
     *
     * <p>One rule is applied beyond the stored restrictions: <strong>hiding the supplier hides the
     * supplier's SKU too.</strong> A supplier code identifies the supplier indirectly — that is
     * what {@link ProtectedField#PRODUCT_SUPPLIER_SKU} exists for — so returning it while blanking
     * the supplier would hand over the answer in a different column. This only ever narrows, which
     * is the safe direction and the direction field restrictions are allowed to move in.
     */
    public ProductView redactedFor(RoleView role) {
        Objects.requireNonNull(role, "role");

        boolean hideSupplier = !role.canSee(ProtectedField.PRODUCT_SUPPLIER);
        boolean hideSupplierSku = hideSupplier
                || !role.canSee(ProtectedField.PRODUCT_SUPPLIER_SKU);
        boolean hideLastPurchasePrice = !role.canSee(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE);

        if (!hideSupplier && !hideSupplierSku && !hideLastPurchasePrice) {
            return this;
        }

        // addAll into an empty EnumSet rather than EnumSet.copyOf: copyOf rejects an empty
        // non-EnumSet argument, and hiddenFields is an unmodifiable wrapper by the time it gets
        // here. Same trap RoleView's constructor documents.
        Set<ProtectedField> hidden = EnumSet.noneOf(ProtectedField.class);
        hidden.addAll(hiddenFields);
        if (hideSupplier) {
            hidden.add(ProtectedField.PRODUCT_SUPPLIER);
        }
        if (hideSupplierSku) {
            hidden.add(ProtectedField.PRODUCT_SUPPLIER_SKU);
        }
        if (hideLastPurchasePrice) {
            hidden.add(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE);
        }

        return new ProductView(
                id, sku, ean, name, brand, type, unitOfMeasure, defaultVatClassId, sellingPrice,
                hideSupplier ? null : supplierId,
                hideSupplierSku ? null : supplierSku,
                serialTracked, bundle,
                hideLastPurchasePrice ? null : lastPurchasePrice,
                active,
                hidden);
    }

    /** True when this field was blanked out by {@link #redactedFor} rather than being unset. */
    public boolean isHidden(ProtectedField field) {
        Objects.requireNonNull(field, "field");
        return hiddenFields.contains(field);
    }

    /** True when anything at all was blanked out of this view. */
    public boolean isRedacted() {
        return !hiddenFields.isEmpty();
    }

    public Optional<String> eanIfAny() {
        return Optional.ofNullable(ean);
    }

    public Optional<String> brandIfAny() {
        return Optional.ofNullable(brand);
    }

    public Optional<Money> sellingPriceIfAny() {
        return Optional.ofNullable(sellingPrice);
    }

    public Optional<Long> supplier() {
        return Optional.ofNullable(supplierId);
    }

    public Optional<String> supplierSkuIfAny() {
        return Optional.ofNullable(supplierSku);
    }

    /**
     * The most recent lot's unit cost, or empty where the product has never been received — and empty
     * when hidden from the viewer, which is why {@link #isHidden} exists to tell the two apart.
     */
    public Optional<UnitCost> lastPurchasePriceIfAny() {
        return Optional.ofNullable(lastPurchasePrice);
    }

    /**
     * True when this product carries stock of its own.
     *
     * <p>Goods do. Services do not — they have no lots. <strong>Nor does a bundle</strong>: it has its
     * own SKU and no stock of its own (brief §5), and its availability is computed from its components,
     * so it can never receive a lot. Both cases answer false here, and for both of them
     * {@code InventoryService.stockOf} refuses rather than answering zero.
     */
    public boolean isStocked() {
        return type.isStocked() && !bundle;
    }

    /**
     * True when this product's stock is individually identified by serial number.
     *
     * <p>Decides the shape of its lots: units rather than a pooled quantity, a location per unit
     * rather than per lot, and — brief §5's exception — write-offs and count corrections that use the
     * named unit's own actual cost rather than FIFO, because serialized stock is not pooled.
     */
    public boolean isSerialTracked() {
        return serialTracked;
    }

    /** True when this is a bundle/composite product (Q11, brief §5). */
    public boolean isBundle() {
        return bundle;
    }
}
