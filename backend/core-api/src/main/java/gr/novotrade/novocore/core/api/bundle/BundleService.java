package gr.novotrade.novocore.core.api.bundle;

import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.util.List;

/**
 * Bundle and composite products. <strong>Q11, answered: built now, to brief §5 in full.</strong>
 *
 * <p>A bundle is a {@link ProductView} with its own SKU, marked as a bundle, holding a component list.
 * It is not a separate kind of entity, because everything else in the system — a barcode scan, a price
 * tag, a Woo listing, an invoice line — has to be able to treat it as one sellable thing.
 *
 * <p><strong>A bundle has no stock of its own.</strong> It cannot receive a lot, and asking
 * {@link InventoryService#stockOf} for one computes how many could be assembled from components,
 * per location, limited by whichever runs out first. That is a real constraint rather than a
 * convention: a bundle with stock of its own would be counted twice, once as itself and once as its
 * parts.
 *
 * <p><strong>Components may not themselves be bundles.</strong> One level deep, and that is
 * deliberate — the same rule and the same reasoning as {@code VatClass}'s island-reduced counterpart.
 * It makes a cycle impossible by construction rather than by a check that has to be got right, and it
 * keeps allocation single-pass: a nested bundle would need its own discount split before it could take
 * a share of its parent's, and nothing in the brief asks for that.
 *
 * <p><strong>What is not here.</strong> Nothing posts, because nothing posts anywhere until step 7 —
 * {@link #decompose} hands back the lines a Sales Invoice will use, and step 9 is what turns them into
 * inventory consumption and COGS. Brief §5's dual-level revenue <em>report</em> is phase 8; what this
 * step owes it is the link between the two levels, which {@link BundleDecomposition} carries and
 * enforces.
 */
public interface BundleService {

    // ---------------------------------------------------------------------------------------
    // Defining
    // ---------------------------------------------------------------------------------------

    /**
     * Makes a product a bundle of the given components, replacing any previous definition.
     *
     * <p><strong>The whole list, every time.</strong> There is deliberately no add-one-component
     * method: a partial change to a bundle leaves the rest in a state nobody chose, which is the same
     * argument that makes a chart-of-accounts reorder name every member. It also means a bundle never
     * exists with zero components, since being a bundle and having components happen in one
     * transaction.
     *
     * @throws InvalidBundleException if the component list is empty; if the product is a service, is
     *     serial-tracked, already has inventory lots, or appears in its own component list; if a
     *     component is unknown, inactive, itself a bundle, or listed twice; or if a component quantity
     *     is not positive or has a fraction its unit of measure does not allow
     * @throws gr.novotrade.novocore.core.api.product.ProductNotFoundException if the product does not
     *     exist
     */
    List<BundleComponentView> define(long bundleProductId, List<NewBundleComponent> components);

    /**
     * Stops a product being a bundle and removes its components.
     *
     * <p><strong>Permitted after the bundle has been sold, and the step 6 obligation is discharged by
     * that being safe rather than by a refusal.</strong> The worry was that dissolving would strand
     * decomposed component lines pointing at something that is no longer a bundle. It does not,
     * because step 9 <em>materialises</em> the decomposition: a sale stores its component lines with
     * their allocated amounts on the invoice, so what a past invoice says is a copy of what was
     * allocated on the day rather than a live read of the current definition. Brief §5's "alias
     * forward, never rewrite history" is satisfied with no alias table, because nothing about a
     * recorded sale changes when the definition does — including when it stops existing.
     *
     * <p><strong>The obligation this creates in exchange, and it is real:</strong> a report showing
     * both revenue levels must read {@code SalesInvoiceLineView.components()} and never
     * {@link #componentsOf}. The current definition can differ from the one that was sold, or be gone.
     *
     * @throws InvalidBundleException if the product is not a bundle
     */
    void dissolve(long bundleProductId);

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    /** True when this product is a bundle. */
    boolean isBundle(long productId);

    /** The components of a bundle, by component SKU. Empty for a product that is not a bundle. */
    List<BundleComponentView> componentsOf(long bundleProductId);

    /**
     * Every bundle this product is a component of.
     *
     * <p>The question to ask before deactivating or discontinuing something: a component going away
     * takes its bundles' sellability with it, so {@code ProductService.deactivate} refuses while this
     * is non-empty rather than leaving a bundle that quietly cannot be assembled.
     */
    List<BundleComponentView> bundlesContaining(long componentProductId);

    /** Every bundle, by SKU. */
    List<ProductView> allBundles();

    /**
     * Bundles that cannot currently be decomposed because a component has no selling price.
     *
     * <p>The same shape as {@code AssetService.withoutDepreciationRate()}: the failure is real, it is
     * knowable in advance, and the alternative to asking is discovering it at the till.
     */
    List<ProductView> bundlesWithUnpricedComponents();

    // ---------------------------------------------------------------------------------------
    // Decomposing
    // ---------------------------------------------------------------------------------------

    /**
     * Splits a bundle sale into component lines, allocating the bundle's value across them in
     * proportion to their standalone values.
     *
     * <p>Brief §5's one core-level rule. The allocation is exact — see {@link BundleAllocation} — so
     * the component lines are the same money as the bundle line and a report can use either.
     *
     * @param bundleQuantity how many bundles. Must be positive, and whole unless the bundle's own unit
     *     of measure allows a fraction.
     * @param bundleTotal what the customer is charged for those bundles, which is normally less than
     *     the components' standalone values add up to. A negative total is allowed and means a return.
     * @throws BundleNotDecomposableException if any component has no standalone selling price
     * @throws InvalidBundleException if the product is not a bundle, or the quantity is not usable
     */
    BundleDecomposition decompose(long bundleProductId, Quantity bundleQuantity, Money bundleTotal);

    /**
     * What the components would cost the customer bought separately.
     *
     * <p>The figure a bundle's discount is measured against, and the denominator of the allocation.
     *
     * @throws BundleNotDecomposableException if any component has no standalone selling price
     */
    Money standaloneValueOf(long bundleProductId);
}
