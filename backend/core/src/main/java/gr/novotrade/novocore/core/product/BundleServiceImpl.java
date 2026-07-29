package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.bundle.BundleComponentLine;
import gr.novotrade.novocore.core.api.bundle.BundleComponentView;
import gr.novotrade.novocore.core.api.bundle.BundleDecomposition;
import gr.novotrade.novocore.core.api.bundle.BundleNotDecomposableException;
import gr.novotrade.novocore.core.api.bundle.BundleService;
import gr.novotrade.novocore.core.api.bundle.InvalidBundleException;
import gr.novotrade.novocore.core.api.bundle.NewBundleComponent;
import gr.novotrade.novocore.core.api.product.ProductNotFoundException;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.ProportionalAllocation;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bundle and composite products (Q11, brief §5).
 *
 * <p>Everything a bundle <em>is</em> lives on {@code Product}: its SKU, its price, its VAT class, its
 * active flag. What lives here is the component list, the rules that keep it coherent, and the one
 * core-level decomposition rule that brief §5 asks for — the rule that today exists three separate
 * times over across WooCommerce, Skroutz and Go.
 *
 * <p><strong>A bundle's availability is not computed here</strong>, it is
 * {@code InventoryService.stockOf}. A caller asking how many there are should not have to know whether
 * the thing is a bundle, so the answer comes from the one place that answers that question for
 * everything. Keeping it there is also what stops these two services depending on each other.
 */
@Service
class BundleServiceImpl implements BundleService {

    private static final String ENTITY_TYPE = "Bundle";

    private final ProductRepository products;
    private final BundleComponentRepository components;
    private final InventoryLotRepository lots;
    private final AuditLogService auditLog;

    BundleServiceImpl(ProductRepository products, BundleComponentRepository components,
            InventoryLotRepository lots, AuditLogService auditLog) {
        this.products = products;
        this.components = components;
        this.lots = lots;
        this.auditLog = auditLog;
    }

    // ---------------------------------------------------------------------------------------
    // Defining
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public List<BundleComponentView> define(
            long bundleProductId, List<NewBundleComponent> requestedComponents) {
        Objects.requireNonNull(requestedComponents, "components");
        Product bundle = loadProduct(bundleProductId);

        if (requestedComponents.isEmpty()) {
            throw new InvalidBundleException(
                    "A bundle needs at least one component. '" + bundle.getSku() + "' with none would "
                            + "be an ordinary product carrying a flag that changes how it is priced, "
                            + "stocked and reported, with nothing behind it.");
        }
        if (bundle.isSerialTracked()) {
            throw new InvalidBundleException(
                    "Product '" + bundle.getSku() + "' is serial-tracked, so it cannot be a bundle: a "
                            + "bundle has no stock of its own and nothing arrives to be numbered.");
        }
        // A bundle's stock is its components'. If it had lots of its own the same goods would be
        // counted twice, once as the bundle and once as its parts.
        if (lots.existsByProductId(bundleProductId)) {
            throw new InvalidBundleException(
                    "Product '" + bundle.getSku() + "' already has inventory lots, so it cannot become "
                            + "a bundle: its stock would be counted twice, once as itself and once as "
                            + "its components.");
        }

        Set<Long> seen = new LinkedHashSet<>();
        List<BundleComponent> resolved = new ArrayList<>();
        for (NewBundleComponent requested : requestedComponents) {
            if (requested.componentProductId() == bundleProductId) {
                throw new InvalidBundleException(
                        "Bundle '" + bundle.getSku() + "' cannot contain itself.");
            }
            if (!seen.add(requested.componentProductId())) {
                throw new InvalidBundleException(
                        "Component " + requested.componentProductId() + " is listed twice in bundle '"
                                + bundle.getSku() + "'. Two rows for one component are two quantities "
                                + "to add up, and the natural mistake is then editing one of them.");
            }

            Product component = products.findById(requested.componentProductId()).orElseThrow(() ->
                    new InvalidBundleException(
                            "No product with id " + requested.componentProductId() + "."));
            if (!component.isActive()) {
                throw new InvalidBundleException(
                        "Component '" + component.getSku() + "' is inactive, so a bundle built on it "
                                + "could not be assembled.");
            }
            // One level deep — the rule VatClass's reduced counterpart uses, and for the same reason:
            // it makes a cycle impossible by construction rather than by a recursive check, and it
            // keeps allocation single-pass. A CHECK cannot see the other row's flag, so this is here.
            if (component.isBundle()) {
                throw new InvalidBundleException(
                        "Component '" + component.getSku() + "' is itself a bundle. Bundles are one "
                                + "level deep: a nested bundle would need its own discount allocated "
                                + "before it could take a share of this one's, which nothing in the "
                                + "brief asks for. List its components directly instead.");
            }
            requireExpressibleQuantity(component, requested.quantity());

            resolved.add(new BundleComponent(bundle, component, requested.quantity()));
        }

        // Replaced wholesale rather than merged. A partial change leaves the rest of a bundle in a
        // state nobody chose — the argument that makes a chart-of-accounts reorder name every member.
        components.deleteByBundleId(bundleProductId);
        components.flush();
        components.saveAll(resolved);
        bundle.setBundle(true);

        auditLog.record("bundle.defined", ENTITY_TYPE, String.valueOf(bundleProductId), Map.of(
                "sku", bundle.getSku(),
                "componentCount", String.valueOf(resolved.size()),
                "components", resolved.stream()
                        .map(component -> component.getComponent().getSku() + " x "
                                + component.getQuantityPerBundle())
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("")));

        return componentsOf(bundleProductId);
    }

    @Override
    @Transactional
    public void dissolve(long bundleProductId) {
        Product bundle = loadProduct(bundleProductId);
        if (!bundle.isBundle()) {
            throw new InvalidBundleException(
                    "Product '" + bundle.getSku() + "' is not a bundle.");
        }

        // Step 9 obligation: once sales exist, dissolving a bundle that has been sold would strand
        // decomposed component lines pointing at something that is no longer a bundle. Brief §5's
        // "alias forward, never rewrite history" is the shape of that answer and it needs the ledger.
        components.deleteByBundleId(bundleProductId);
        bundle.setBundle(false);

        auditLog.record("bundle.dissolved", ENTITY_TYPE, String.valueOf(bundleProductId),
                Map.of("sku", bundle.getSku()));
    }

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean isBundle(long productId) {
        return loadProduct(productId).isBundle();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BundleComponentView> componentsOf(long bundleProductId) {
        return components.findByBundleIdOrderByComponentSkuAsc(bundleProductId).stream()
                .map(BundleServiceImpl::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BundleComponentView> bundlesContaining(long componentProductId) {
        return components.findByComponentIdOrderByBundleSkuAsc(componentProductId).stream()
                .map(BundleServiceImpl::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> allBundles() {
        return products.findByBundleTrueOrderBySkuAsc().stream()
                .map(BundleServiceImpl::toProductView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> bundlesWithUnpricedComponents() {
        return products.findByBundleTrueOrderBySkuAsc().stream()
                .filter(bundle -> components.findByBundleIdOrderByComponentSkuAsc(bundle.getId())
                        .stream()
                        .anyMatch(component -> component.getComponent().getSellingPrice() == null))
                .map(BundleServiceImpl::toProductView)
                .toList();
    }

    // ---------------------------------------------------------------------------------------
    // Reading — redacted for a viewer
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> allBundlesFor(RoleView viewer) {
        return redact(allBundles(), viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> bundlesWithUnpricedComponentsFor(RoleView viewer) {
        return redact(bundlesWithUnpricedComponents(), viewer);
    }

    /**
     * The same two lines as {@code ProductServiceImpl.redact}, deliberately not shared with it.
     *
     * <p>Sharing would mean one of these slices reaching into the other's implementation, or a
     * helper in {@code core-api} whose only job is to call a method that is already there.
     * {@code ProductView.redactedFor} <em>is</em> the single implementation of the rule — this is
     * two calls to it, not a second copy of it.
     */
    private static List<ProductView> redact(List<ProductView> bundles, RoleView viewer) {
        Objects.requireNonNull(viewer, "viewer");
        // Refused rather than returned empty. "You may not see products" and "there are no
        // bundles" are different answers, and an empty list cannot express the difference.
        viewer.requireView(Section.PRODUCTS);
        return bundles.stream().map(bundle -> bundle.redactedFor(viewer)).toList();
    }

    // ---------------------------------------------------------------------------------------
    // Decomposing — brief §5's one core-level rule
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public BundleDecomposition decompose(
            long bundleProductId, Quantity bundleQuantity, Money bundleTotal) {
        Objects.requireNonNull(bundleQuantity, "bundleQuantity");
        Objects.requireNonNull(bundleTotal, "bundleTotal");
        Product bundle = requireBundle(bundleProductId);

        if (!bundleQuantity.isPositive()) {
            throw new InvalidBundleException(
                    "Bundle quantity " + bundleQuantity + " is not positive. A return is a negative "
                            + "total against a positive quantity, not a negative quantity.");
        }
        requireExpressibleQuantity(bundle, bundleQuantity);

        List<BundleComponent> bundleComponents =
                components.findByBundleIdOrderByComponentSkuAsc(bundleProductId);
        if (bundleComponents.isEmpty()) {
            throw BundleNotDecomposableException.noComponents(bundleProductId);
        }

        // The weights: each component's own standalone value inside one bundle. An unpriced component
        // throws rather than weighing zero — a zero weight would silently push the whole bundle's
        // revenue onto the priced components and report this one as pure margin.
        List<BigDecimal> weights = new ArrayList<>(bundleComponents.size());
        for (BundleComponent component : bundleComponents) {
            weights.add(standaloneValueOf(bundleProductId, component).amount());
        }

        List<Money> allocations = ProportionalAllocation.proportionally(bundleTotal, weights);

        List<BundleComponentLine> lines = new ArrayList<>(bundleComponents.size());
        for (int i = 0; i < bundleComponents.size(); i++) {
            BundleComponent component = bundleComponents.get(i);
            // Quantity.times rather than a raw multiply: two six-decimal values produce twelve, and
            // one whole bundle containing one whole grinder would otherwise fail on its own zeros.
            Quantity lineQuantity = component.getQuantityPerBundle().times(bundleQuantity);
            lines.add(new BundleComponentLine(
                    bundleProductId,
                    component.getComponent().getId(),
                    component.getComponent().getSku(),
                    component.getComponent().getName(),
                    lineQuantity,
                    allocations.get(i),
                    component.getComponent().getType().isStocked()));
        }

        return new BundleDecomposition(
                bundleProductId, bundle.getSku(), bundleQuantity, bundleTotal, lines);
    }

    @Override
    @Transactional(readOnly = true)
    public Money standaloneValueOf(long bundleProductId) {
        requireBundle(bundleProductId);
        List<BundleComponent> bundleComponents =
                components.findByBundleIdOrderByComponentSkuAsc(bundleProductId);
        if (bundleComponents.isEmpty()) {
            throw BundleNotDecomposableException.noComponents(bundleProductId);
        }

        Money total = null;
        for (BundleComponent component : bundleComponents) {
            Money value = standaloneValueOf(bundleProductId, component);
            total = total == null ? value : total.plus(value);
        }
        return total;
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    /**
     * One component's standalone value inside a single bundle — its own price extended across its
     * quantity, rounded once.
     */
    private static Money standaloneValueOf(long bundleProductId, BundleComponent component) {
        Money price = component.getComponent().getSellingPrice();
        if (price == null) {
            throw BundleNotDecomposableException.unpricedComponent(
                    bundleProductId, component.getComponent().getSku());
        }
        return price.times(component.getQuantityPerBundle().value(), java.math.RoundingMode.HALF_UP);
    }

    private Product loadProduct(long productId) {
        return products.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private Product requireBundle(long productId) {
        Product product = loadProduct(productId);
        if (!product.isBundle()) {
            throw new InvalidBundleException(
                    "Product '" + product.getSku() + "' is not a bundle, so it has nothing to "
                            + "decompose into.");
        }
        return product;
    }

    /** The V11 rule, read off the component's own unit rather than from a list kept here. */
    private static void requireExpressibleQuantity(Product product, Quantity quantity) {
        if (product.getUnitOfMeasure().isFractionalQuantityAllowed()) {
            return;
        }
        if (quantity.value().stripTrailingZeros().scale() > 0) {
            throw new InvalidBundleException(
                    "Quantity " + quantity + " has a fraction, and '" + product.getSku()
                            + "' is measured in '" + product.getUnitOfMeasure().getCode()
                            + "', which does not allow one.");
        }
    }

    private static BundleComponentView toView(BundleComponent component) {
        Product componentProduct = component.getComponent();
        return new BundleComponentView(
                component.getBundle().getId(),
                componentProduct.getId(),
                componentProduct.getSku(),
                componentProduct.getName(),
                component.getQuantityPerBundle(),
                UnitOfMeasureServiceImpl.toView(componentProduct.getUnitOfMeasure()),
                componentProduct.getSellingPrice(),
                componentProduct.getType().isStocked(),
                componentProduct.isActive());
    }

    /**
     * A bundle projected as a product, without its last purchase price.
     *
     * <p>Left null rather than looked up: a bundle has no stock of its own and therefore no lots, so
     * there is no purchase price to report. Going through {@code ProductService} for this would be a
     * bean cycle for a value that is always absent.
     */
    private static ProductView toProductView(Product bundle) {
        return new ProductView(
                bundle.getId(),
                bundle.getSku(),
                bundle.getEan(),
                bundle.getName(),
                bundle.getType(),
                UnitOfMeasureServiceImpl.toView(bundle.getUnitOfMeasure()),
                bundle.getDefaultVatClassId(),
                bundle.getSellingPrice(),
                bundle.getSupplierId(),
                bundle.getSupplierSku(),
                bundle.isSerialTracked(),
                bundle.isBundle(),
                null,
                bundle.isActive(),
                Set.of());
    }
}
