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
 * variants take the viewing role and apply {@link ProductView#redactedFor}. <strong>Every plain read
 * has a {@code ...For} counterpart</strong>, which is what makes the rule below followable; until
 * step 14 four of them were missing, so a controller wanting active products or a SKU lookup had no
 * redacted method to call at all.
 *
 * <p><strong>Anything answering a request from a person must use the {@code ...For} variants.</strong>
 * That is a rule this interface states rather than enforces, and the honest reason is that both
 * kinds of caller genuinely exist: making redaction mandatory would mean inventing a
 * pretend-role for the posting rules to pass, which is a worse failure mode than a named
 * convention — a "system role" that sees everything is exactly the thing that later gets reused by
 * a controller. The {@code For} suffix is in the name so its absence is visible at the call site.
 *
 * <p><strong>Since step 14 it is enforced for the web layer specifically.</strong> An architecture
 * rule forbids anything in {@code ..core.web..} from calling the unredacted reads, so a controller
 * cannot reach them even by accident. The convention still governs everywhere else, because
 * everywhere else has legitimate unredacted callers.
 *
 * <p>Three fields are restricted for Remote/Order Staff:
 * {@link ProtectedField#PRODUCT_LAST_PURCHASE_PRICE}, {@link ProtectedField#PRODUCT_SUPPLIER} and
 * {@link ProtectedField#PRODUCT_SUPPLIER_SKU}. An order picker needs to know what a product is and
 * what it sells for; they have no need to know what it cost us or who supplies it.
 *
 * <p><strong>Stock is not here, and that is the answer to Q7 rather than an omission.</strong> Stock
 * is not one number: the location lives with the quantity and only the Inventory location is sellable,
 * so it is {@code InventoryService.stockOf} returning {@code StockLevels} — per location plus a
 * computed sellable figure. {@link ProductView#lastPurchasePrice()} is the one derived figure that does
 * appear here, because it is one of the three restricted fields and belongs behind the same redaction.
 *
 * <p><strong>Bundles are {@code BundleService}</strong> (Q11, answered: built). This interface carries
 * the flag and the rules that follow from it — a bundle cannot receive stock, and a product cannot be
 * a bundle and serial-tracked — while the component list and the decomposition live there.
 */
public interface ProductService {

    // ---------------------------------------------------------------------------------------
    // Reading — unredacted, for the core's own rules
    // ---------------------------------------------------------------------------------------

    /** Every product, active and inactive, by SKU. Unredacted. */
    List<ProductView> all();

    /** Active products only, by SKU. Unredacted. */
    List<ProductView> active();

    /**
     * Products whose SKU, name, brand, barcode or supplier's SKU contains the term anywhere,
     * ignoring case and accents. Unredacted.
     *
     * <p>Brand is one of the searched columns: "which Rocket machines do we stock" is a question
     * the catalogue could not answer at all before, since a brand only appeared in a title if
     * somebody happened to type it there.
     *
     * <p><strong>The supplier's SKU is searched, and it is a restricted field.</strong>
     * {@link ProtectedField#PRODUCT_SUPPLIER_SKU} is hidden from Remote/Order Staff, so a role that
     * cannot see that column can nonetheless find a row by matching it — which would let somebody
     * confirm a supplier code by guessing at it. {@link #searchFor} is where that is dealt with: it
     * searches only the columns the viewer may actually see. This unredacted variant searches all
     * five, which is right for the core's own callers and wrong for anything answering a person.
     *
     * @param term matched as a substring; null or blank means no filter. Wildcards are literal.
     * @param activeOnly whether to restrict to active products, combining with the term
     */
    List<ProductView> search(String term, boolean activeOnly);

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
     * As {@link #all()} — every product, active and inactive — redacted for the viewer.
     *
     * <p><strong>This used to return only the active ones</strong>, contradicting its own name and
     * leaving {@link #all()} with no redacted counterpart. Corrected in step 14, when the first
     * controller needed both answers and could get only one. If you want active products only, that
     * is {@link #activeFor} and always should have been.
     *
     * @throws gr.novotrade.novocore.core.api.security.SectionAccessDeniedException if the role
     *     cannot view products at all. Refused rather than returned empty: "you may not see this"
     *     and "there are none" are different answers and a caller cannot tell them apart from an
     *     empty list.
     */
    List<ProductView> allFor(RoleView viewer);

    /** As {@link #active()}, redacted for the viewer. */
    List<ProductView> activeFor(RoleView viewer);

    /**
     * As {@link #search}, <strong>and it searches fewer columns for a restricted viewer.</strong>
     *
     * <p>This is the one {@code ...For} variant that does more than redact its results, and the
     * reason is worth reading. Three of this product's fields are restricted, and one of them —
     * {@link ProtectedField#PRODUCT_SUPPLIER_SKU} — is a searched column. Redaction alone would blank
     * it in the response and still let a row be <em>found</em> by matching it, so Remote/Order Staff
     * could recover a supplier's code one character at a time by watching which prefixes return a
     * hit. Every character is confirmed by a result the role is otherwise entitled to see, so nothing
     * looks unusual while it happens.
     *
     * <p>So the supplier's SKU is searched only for a viewer who may see it. A role that may not gets
     * SKU, name, brand and barcode, and the results are redacted as usual on the way out. The consequence to
     * be aware of is that <strong>the same term can return fewer rows for a restricted role</strong>
     * — which is correct, and is the same shape as the sections a role cannot list at all.
     *
     * @throws gr.novotrade.novocore.core.api.security.SectionAccessDeniedException if the role cannot
     *     view products at all
     */
    List<ProductView> searchFor(String term, boolean activeOnly, RoleView viewer);

    /** As {@link #find}, redacted for the viewer. */
    Optional<ProductView> findFor(long id, RoleView viewer);

    /** As {@link #require}, redacted for the viewer. */
    ProductView requireFor(long id, RoleView viewer);

    /** As {@link #findBySku}, redacted for the viewer. */
    Optional<ProductView> findBySkuFor(String sku, RoleView viewer);

    /**
     * As {@link #findByEan}, redacted for the viewer.
     *
     * <p>The one a barcode scanner behind a screen must call. Remote/Order Staff is exactly the role
     * that scans, and exactly the role the three restricted fields exist for.
     */
    Optional<ProductView> findByEanFor(String ean, RoleView viewer);

    /**
     * As {@link #bySupplier}, redacted for the viewer.
     *
     * <p>Note what redaction does <em>not</em> do here: it hides each product's supplier field, but
     * the caller supplied the supplier id, so the association is not concealed by this call. A role
     * that must not learn who supplies what should not be able to view {@code SUPPLIERS} either —
     * which is a section grant, not a field restriction, and is how that is actually enforced.
     */
    List<ProductView> bySupplierFor(long supplierId, RoleView viewer);

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
     * <p><strong>Refused once the product has lots.</strong> Reinterpreting 12 pieces as 12 kilograms
     * is not a units change, it is a different quantity — and every lot, every future consumption and
     * every posted cost behind that product is denominated in the old unit. The step 5 obligation,
     * discharged: there is something to guard now.
     *
     * @throws InvalidProductException if the unit does not exist or is inactive, if the product has any
     *     inventory lot, or if the new unit forbids fractions the product's bundle components need
     */
    ProductView changeUnitOfMeasure(long id, long unitOfMeasureId);

    /**
     * Switches a product between pooled and serial-tracked stock.
     *
     * <p><strong>Refused once the product has lots</strong>, for the same reason as the unit of
     * measure and then some: a lot is received in one shape or the other, and there is no honest way to
     * turn a pooled quantity of five into five identified units — the serial numbers were never
     * recorded. This exists for the window between creating a product and receiving its first stock,
     * which is where the mistake is actually correctable.
     *
     * @throws InvalidProductException if the product is a service or a bundle (neither has units to
     *     number), or if it already has inventory lots
     */
    ProductView changeSerialTracking(long id, boolean serialTracked);

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

    /**
     * Sets or clears the brand.
     *
     * <p>Null clears it, which is how "this product has no brand" is said — an ordinary state here,
     * not an unfilled field. Blank is treated as null rather than stored, so the column has one
     * representation of "none" and a brand search cannot half-match a product that has none.
     *
     * <p>No uniqueness check, deliberately: two products may spell a brand differently and that is
     * accepted, because a brand is free text rather than a reference. See {@code V29}.
     */
    ProductView changeBrand(long id, String brand);

    /**
     * Deactivates a product.
     *
     * <p><strong>Refused while the product is a component of an active bundle.</strong> A bundle whose
     * component has been discontinued cannot be assembled, and deactivating quietly would leave a
     * sellable-looking bundle that fails at the till — the same reasoning that refuses to deactivate a
     * unit of measure a product still uses. {@code BundleService.bundlesContaining} names them, and
     * either the bundle is dissolved or the component is replaced first.
     *
     * @throws InvalidProductException if an active bundle still lists this product as a component
     */
    void deactivate(long id);

    void reactivate(long id);
}
