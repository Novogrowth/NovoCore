package gr.novotrade.novocore.core.api.product;

import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.shared.Money;
import java.util.List;
import java.util.Optional;

/**
 * Products: the catalogue, and the sub-ledger behind Inventory once lots exist.
 *
 * <p><strong>Read methods come in two forms, and the difference matters.</strong> The plain ones
 * ({@link #all()}, {@link #require(long)}) return everything, which is what NovoCore's own posting
 * and costing rules need — a FIFO calculation cannot work from a redacted cost. The {@code ...For}
 * variants ({@link #allFor}, {@link #findFor}, {@link #requireFor}) take the viewing role and apply
 * {@link ProductView#redactedFor}.
 *
 * <p><strong>Anything answering a request from a person must use the {@code ...For} variants.</strong>
 * That is a rule this interface states rather than enforces, and the honest reason is that both
 * kinds of caller genuinely exist: making redaction mandatory would mean inventing a
 * pretend-role for the posting rules to pass, which is a worse failure mode than a named
 * convention — a "system role" that sees everything is exactly the thing that later gets reused by
 * a controller. The {@code For} suffix is in the name so its absence is visible at the call site.
 *
 * <p>Three fields are restricted for Remote/Order Staff:
 * {@link ProtectedField#PRODUCT_LAST_PURCHASE_PRICE}, {@link ProtectedField#PRODUCT_SUPPLIER} and
 * {@link ProtectedField#PRODUCT_SUPPLIER_SKU}. An order picker needs to know what a product is and
 * what it sells for; they have no need to know what it cost us or who supplies it.
 *
 * <p><strong>Not here yet.</strong> Stock, in any form — it comes from lots (step 6) and is not one
 * number (Q7). Bundles/composite products, which are in brief §5 but were left out of the agreed
 * Phase 1 scope and are still an open question (Q11), so no bundle flag is carried: a flag nothing
 * honours reads as a half-built feature to whoever finds it next. Last purchase price is on
 * {@link ProductView} but always empty, being derived from lot costs.
 */
public interface ProductService {

    // ---------------------------------------------------------------------------------------
    // Reading — unredacted, for the core's own rules
    // ---------------------------------------------------------------------------------------

    /** Every product, active and inactive, by SKU. Unredacted. */
    List<ProductView> all();

    /** Active products only, by SKU. Unredacted. */
    List<ProductView> active();

    Optional<ProductView> find(long id);

    /** @throws ProductNotFoundException if absent */
    ProductView require(long id);

    /** By NovoCore's own SKU, which is unique. Unredacted. */
    Optional<ProductView> findBySku(String sku);

    /** @throws ProductNotFoundException if absent */
    ProductView requireBySku(String sku);

    /**
     * By barcode, for scanner-driven entry points.
     *
     * <p>Empty rather than throwing: an unrecognised barcode is an ordinary outcome, and brief §5's
     * barcode-first flow falls back to a supplier link and then to manual entry when it happens.
     */
    Optional<ProductView> findByEan(String ean);

    /** Products supplied by one supplier. Unredacted, so not for a Remote/Order Staff response. */
    List<ProductView> bySupplier(long supplierId);

    // ---------------------------------------------------------------------------------------
    // Reading — redacted for a viewer
    // ---------------------------------------------------------------------------------------

    /**
     * Active products as this role may see them.
     *
     * @throws gr.novotrade.novocore.core.api.security.SectionAccessDeniedException if the role
     *     cannot view products at all. Refused rather than returned empty: "you may not see this"
     *     and "there are none" are different answers and a caller cannot tell them apart from an
     *     empty list.
     */
    List<ProductView> allFor(RoleView viewer);

    /** As {@link #find}, redacted for the viewer. */
    Optional<ProductView> findFor(long id, RoleView viewer);

    /** As {@link #require}, redacted for the viewer. */
    ProductView requireFor(long id, RoleView viewer);

    // ---------------------------------------------------------------------------------------
    // Changing
    // ---------------------------------------------------------------------------------------

    /**
     * Adds a product.
     *
     * @throws InvalidProductException if the SKU or EAN duplicates an existing product, the VAT
     *     class, unit of measure or supplier does not exist or is inactive, or a supplier SKU is
     *     given with no supplier
     */
    ProductView create(NewProduct request);

    /**
     * Changes the unit a product's quantity is expressed in.
     *
     * <p>Once lots exist (step 6) this will need to refuse a change on a product that has stock:
     * reinterpreting 12 pieces as 12 kilograms is not a units change, it is a different quantity.
     * There is nothing to guard yet, and stating the obligation here is what makes it findable.
     *
     * @throws InvalidProductException if the unit does not exist or is inactive
     */
    ProductView changeUnitOfMeasure(long id, long unitOfMeasureId);

    /** @throws InvalidProductException if the name is blank */
    ProductView rename(long id, String newName);

    /**
     * Sets or clears the selling price.
     *
     * <p>A price of zero is refused. It is indistinguishable from an unset price on a screen and
     * produces an invoice line worth nothing without anybody choosing to give the goods away; null
     * says "no price yet" unambiguously, which is what a caller reaching for zero actually means.
     *
     * @throws InvalidProductException if the price is zero or negative
     */
    ProductView changeSellingPrice(long id, Money sellingPrice);

    /**
     * Changes the product's default VAT class.
     *
     * @throws InvalidProductException if the VAT class does not exist or is inactive
     */
    ProductView changeDefaultVatClass(long id, long vatClassId);

    /**
     * Sets or clears the supplier and the supplier's code for this product (Q5).
     *
     * <p>Both together, not two methods. They are only meaningful as a pair — that is the whole
     * content of Q5 — and separate setters would allow a supplier SKU to outlive the supplier it
     * belongs to, which is the state the schema refuses.
     *
     * @throws InvalidProductException if the supplier does not exist or is inactive, or a supplier
     *     SKU is given with no supplier
     */
    ProductView changeSupplier(long id, Long supplierId, String supplierSku);

    /** Sets or clears the barcode. @throws InvalidProductException if another product has it */
    ProductView changeEan(long id, String ean);

    void deactivate(long id);

    void reactivate(long id);
}
