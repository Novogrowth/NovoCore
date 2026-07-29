package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.product.InvalidProductException;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductNotFoundException;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.supplier.SupplierView;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The VAT class and supplier references are validated through {@link VatClassService} and
 * {@link SupplierService} — published interfaces, not the entities behind them, which are
 * package-private in their own slices (ADR 0003).
 *
 * <p>The {@code ...For} methods apply {@link ProductView#redactedFor}, which is where step 4's
 * field-restriction obligation is discharged. The redaction logic itself lives on the view, so this
 * class holds no copy of the rule.
 *
 * <p><strong>Lots and bundle components are read from their repositories directly, not through
 * {@code InventoryService} or {@code BundleService}.</strong> They are the same slice of the core —
 * the Inventory control account's sub-ledger is Product-Lot, one thing — so this is the same case as
 * {@code UnitOfMeasure}. It is also the only arrangement that works: a product's last purchase price
 * comes from a lot while a lot's validity depends on its product, and two services calling each other
 * is a bean cycle.
 */
@Service
class ProductServiceImpl implements ProductService {

    private static final String ENTITY_TYPE = "Product";

    private final ProductRepository repository;
    private final UnitOfMeasureRepository unitsOfMeasure;
    private final InventoryLotRepository lots;
    private final BundleComponentRepository bundleComponents;
    private final VatClassService vatClasses;
    private final SupplierService suppliers;
    private final AuditLogService auditLog;

    ProductServiceImpl(ProductRepository repository, UnitOfMeasureRepository unitsOfMeasure,
            InventoryLotRepository lots, BundleComponentRepository bundleComponents,
            VatClassService vatClasses, SupplierService suppliers, AuditLogService auditLog) {
        this.repository = repository;
        // The unit repository directly, not through UnitOfMeasureService: units live in this
        // package, so they are the same slice of the core rather than another aggregate reached
        // through a published interface. The VAT class and supplier above are the other case.
        this.unitsOfMeasure = unitsOfMeasure;
        this.lots = lots;
        this.bundleComponents = bundleComponents;
        this.vatClasses = vatClasses;
        this.suppliers = suppliers;
        this.auditLog = auditLog;
    }

    // ---------------------------------------------------------------------------------------
    // Reading — unredacted
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> all() {
        return toViews(repository.findAllByOrderBySkuAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> active() {
        return toViews(repository.findByActiveTrueOrderBySkuAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductView> find(long id) {
        return repository.findById(id).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductView require(long id) {
        return find(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductView> findBySku(String sku) {
        String normalised = optionalText(sku);
        if (normalised == null) {
            return Optional.empty();
        }
        return repository.findBySkuIgnoreCase(normalised).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductView requireBySku(String sku) {
        return findBySku(sku).orElseThrow(() -> ProductNotFoundException.forSku(sku));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductView> findByEan(String ean) {
        String normalised = optionalText(ean);
        if (normalised == null) {
            // A blank scan matches nothing. Matching the first product with no barcode would turn
            // a misread into a confidently wrong product on an invoice.
            return Optional.empty();
        }
        return repository.findByEan(normalised).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> bySupplier(long supplierId) {
        return toViews(repository.findBySupplierIdOrderBySkuAsc(supplierId));
    }

    // ---------------------------------------------------------------------------------------
    // Reading — redacted for a viewer
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> allFor(RoleView viewer) {
        // Mirrors all(), including inactive products. It called active() until step 14, which
        // contradicted the name and left all() with no redacted counterpart at all.
        return redact(all(), viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> activeFor(RoleView viewer) {
        return redact(active(), viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductView> findFor(long id, RoleView viewer) {
        requireProductAccess(viewer);
        return find(id).map(product -> product.redactedFor(viewer));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductView requireFor(long id, RoleView viewer) {
        return findFor(id, viewer).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductView> findBySkuFor(String sku, RoleView viewer) {
        requireProductAccess(viewer);
        return findBySku(sku).map(product -> product.redactedFor(viewer));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductView> findByEanFor(String ean, RoleView viewer) {
        requireProductAccess(viewer);
        return findByEan(ean).map(product -> product.redactedFor(viewer));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> bySupplierFor(long supplierId, RoleView viewer) {
        return redact(bySupplier(supplierId), viewer);
    }

    private List<ProductView> redact(List<ProductView> products, RoleView viewer) {
        requireProductAccess(viewer);
        return products.stream().map(product -> product.redactedFor(viewer)).toList();
    }

    /**
     * Refused rather than returned empty. "You may not see products" and "there are no products"
     * are different answers, and an empty list cannot express the difference.
     */
    private static void requireProductAccess(RoleView viewer) {
        Objects.requireNonNull(viewer, "viewer");
        viewer.requireView(Section.PRODUCTS);
    }

    // ---------------------------------------------------------------------------------------
    // Changing
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public ProductView create(NewProduct request) {
        Objects.requireNonNull(request, "request");
        String sku = requireText(request.sku(), "SKU");
        String name = requireText(request.name(), "Product name");
        String ean = optionalText(request.ean());
        String supplierSku = optionalText(request.supplierSku());

        if (repository.existsBySkuIgnoreCase(sku)) {
            throw new InvalidProductException("A product with SKU '" + sku + "' already exists.");
        }
        if (ean != null && repository.existsByEan(ean)) {
            throw new InvalidProductException(
                    "A product with barcode '" + ean + "' already exists. A shared barcode would "
                            + "make a scan ambiguous, which is the one thing scanning is for.");
        }
        requireActiveVatClass(request.defaultVatClassId());
        UnitOfMeasure unit = requireActiveUnitOfMeasure(request.unitOfMeasureId());
        requireSupplierPair(request.supplierId(), supplierSku);
        requireUsablePrice(request.sellingPrice());

        // A service has no units to number. Refused here as well as by CHECK, so the message names the
        // reason instead of surfacing as a constraint violation.
        if (request.serialTracked() && !request.type().isStocked()) {
            throw new InvalidProductException(
                    "Product '" + sku + "' is a " + request.type() + ", so it cannot be serial-"
                            + "tracked: a service has no units to give serial numbers to.");
        }

        Product saved = repository.save(new Product(
                sku, ean, name, request.type(), unit,
                request.defaultVatClassId(), request.sellingPrice(),
                request.supplierId(), supplierSku, request.serialTracked()));

        auditLog.record("product.created", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "sku", sku,
                "name", name,
                "type", request.type().name(),
                "serialTracked", String.valueOf(request.serialTracked()),
                "defaultVatClassId", String.valueOf(request.defaultVatClassId())));

        return toView(saved);
    }

    @Override
    @Transactional
    public ProductView rename(long id, String newName) {
        String name = requireText(newName, "Product name");
        Product product = load(id);

        String previous = product.getName();
        product.rename(name);

        auditLog.record("product.renamed", ENTITY_TYPE, String.valueOf(id),
                Map.of("sku", product.getSku(), "from", previous, "to", name));

        return toView(product);
    }

    @Override
    @Transactional
    public ProductView changeSellingPrice(long id, Money sellingPrice) {
        Product product = load(id);
        requireUsablePrice(sellingPrice);

        product.setSellingPrice(sellingPrice);

        auditLog.record("product.selling-price-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "sku", product.getSku(),
                "sellingPrice", sellingPrice == null ? "(cleared)" : sellingPrice.toString()));

        return toView(product);
    }

    @Override
    @Transactional
    public ProductView changeDefaultVatClass(long id, long vatClassId) {
        Product product = load(id);
        VatClassView vatClass = requireActiveVatClass(vatClassId);

        product.changeDefaultVatClass(vatClassId);

        auditLog.record("product.vat-class-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of("sku", product.getSku(), "vatClass", vatClass.code()));

        return toView(product);
    }

    @Override
    @Transactional
    public ProductView changeUnitOfMeasure(long id, long unitOfMeasureId) {
        Product product = load(id);
        UnitOfMeasure unit = requireActiveUnitOfMeasure(unitOfMeasureId);

        // The step 5 obligation, discharged. Reinterpreting 12 pieces as 12 kilograms is not a units
        // change, it is a different quantity — and every lot, every future consumption and every posted
        // cost behind this product is denominated in the old unit.
        if (lots.existsByProductId(id)) {
            throw new InvalidProductException(
                    "Product '" + product.getSku() + "' has inventory lots, so its unit of measure "
                            + "cannot be changed: reinterpreting a recorded quantity in a different "
                            + "unit is not a correction, it is a different quantity. Write the stock "
                            + "off and receive it again if the unit was genuinely recorded wrong.");
        }

        // The V11 obligation applied in the other direction: a bundle component quantity that is
        // fractional cannot survive a move to a unit that forbids fractions.
        if (!unit.isFractionalQuantityAllowed()) {
            for (BundleComponent component : bundleComponents.findByComponentIdOrderByBundleSkuAsc(id)) {
                if (hasFraction(component.getQuantityPerBundle().value())) {
                    throw new InvalidProductException(
                            "Product '" + product.getSku() + "' appears in bundle '"
                                    + component.getBundle().getSku() + "' with a fractional quantity "
                                    + component.getQuantityPerBundle() + ", which unit '"
                                    + unit.getCode() + "' does not allow.");
                }
            }
        }

        String previous = product.getUnitOfMeasure().getCode();
        product.changeUnitOfMeasure(unit);

        auditLog.record("product.unit-of-measure-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "sku", product.getSku(),
                "from", previous,
                "to", unit.getCode()));

        return toView(product);
    }

    @Override
    @Transactional
    public ProductView changeSerialTracking(long id, boolean serialTracked) {
        Product product = load(id);

        if (product.isSerialTracked() == serialTracked) {
            return toView(product);
        }
        if (serialTracked && !product.getType().isStocked()) {
            throw new InvalidProductException(
                    "Product '" + product.getSku() + "' is a " + product.getType() + ", so it cannot "
                            + "be serial-tracked: a service has no units to give serial numbers to.");
        }
        if (serialTracked && product.isBundle()) {
            throw new InvalidProductException(
                    "Product '" + product.getSku() + "' is a bundle, so it cannot be serial-tracked: "
                            + "a bundle has no stock of its own and nothing arrives to be numbered.");
        }
        // The reason this is not freely changeable. A pooled quantity of five cannot become five
        // identified units, because the serial numbers were never recorded; going the other way would
        // discard identities that warranty and service history depend on.
        if (lots.existsByProductId(id)) {
            throw new InvalidProductException(
                    "Product '" + product.getSku() + "' already has inventory lots, so its serial "
                            + "tracking cannot be changed. A lot is received in one shape or the "
                            + "other: a pooled quantity has no serial numbers to recover, and "
                            + "discarding real ones would lose the history attached to them.");
        }

        product.changeSerialTracking(serialTracked);

        auditLog.record("product.serial-tracking-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "sku", product.getSku(),
                "serialTracked", String.valueOf(serialTracked)));

        return toView(product);
    }

    @Override
    @Transactional
    public ProductView changeSupplier(long id, Long supplierId, String supplierSku) {
        Product product = load(id);
        String normalisedSupplierSku = optionalText(supplierSku);
        requireSupplierPair(supplierId, normalisedSupplierSku);

        product.changeSupplier(supplierId, normalisedSupplierSku);

        auditLog.record("product.supplier-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "sku", product.getSku(),
                "supplierId", supplierId == null ? "(cleared)" : String.valueOf(supplierId),
                "supplierSku", normalisedSupplierSku == null ? "(none)" : normalisedSupplierSku));

        return toView(product);
    }

    @Override
    @Transactional
    public ProductView changeEan(long id, String ean) {
        Product product = load(id);
        String normalised = optionalText(ean);

        if (normalised != null && !normalised.equals(product.getEan())
                && repository.existsByEan(normalised)) {
            throw new InvalidProductException(
                    "Another product already has barcode '" + normalised + "'.");
        }

        product.changeEan(normalised);

        auditLog.record("product.ean-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "sku", product.getSku(),
                "ean", normalised == null ? "(cleared)" : normalised));

        return toView(product);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        Product product = load(id);
        if (!product.isActive()) {
            return;
        }

        // Refused rather than cascaded, the same stance UnitOfMeasureService takes on a unit a product
        // still uses. A bundle whose component has been discontinued cannot be assembled, and letting
        // this through would leave a bundle that looks sellable and fails at the till.
        List<String> blockingBundles = bundleComponents.findByComponentIdOrderByBundleSkuAsc(id).stream()
                .map(BundleComponent::getBundle)
                .filter(Product::isActive)
                .map(Product::getSku)
                .toList();
        if (!blockingBundles.isEmpty()) {
            throw new InvalidProductException(
                    "Product '" + product.getSku() + "' is a component of active bundle(s) "
                            + String.join(", ", blockingBundles) + ", which could not be assembled "
                            + "without it. Dissolve or re-define the bundle first, or deactivate it "
                            + "too.");
        }

        product.setActive(false);
        auditLog.record("product.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("sku", product.getSku()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        Product product = load(id);
        if (product.isActive()) {
            return;
        }
        product.setActive(true);
        auditLog.record("product.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("sku", product.getSku()));
    }

    private Product load(long id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * An inactive unit is refused for the same reason an inactive VAT class is: a unit is
     * deactivated precisely so nothing new is expressed in it.
     */
    private UnitOfMeasure requireActiveUnitOfMeasure(long unitOfMeasureId) {
        UnitOfMeasure unit = unitsOfMeasure.findById(unitOfMeasureId).orElseThrow(() ->
                new InvalidProductException("No unit of measure with id " + unitOfMeasureId + "."));
        if (!unit.isActive()) {
            throw new InvalidProductException(
                    "Unit of measure '" + unit.getCode() + "' is inactive, so a product cannot be "
                            + "newly expressed in it.");
        }
        return unit;
    }

    private VatClassView requireActiveVatClass(long vatClassId) {
        VatClassView vatClass = vatClasses.find(vatClassId).orElseThrow(() ->
                new InvalidProductException("No VAT class with id " + vatClassId + "."));
        if (!vatClass.active()) {
            throw new InvalidProductException(
                    "VAT class '" + vatClass.code() + "' is inactive, so it cannot be a product's "
                            + "default rate. A class is deactivated precisely so new documents "
                            + "stop using it.");
        }
        return vatClass;
    }

    /**
     * Q5 enforced in code as well as in the schema.
     *
     * <p>A supplier SKU without a supplier is the state that made the field meaningless. The
     * reverse — a supplier with no supplier SKU — is perfectly ordinary and permitted.
     */
    private void requireSupplierPair(Long supplierId, String supplierSku) {
        if (supplierSku != null && supplierId == null) {
            throw new InvalidProductException(
                    "A supplier SKU needs a supplier: '" + supplierSku + "' identifies nothing "
                            + "without knowing whose code it is.");
        }
        if (supplierId == null) {
            return;
        }
        SupplierView supplier = suppliers.find(supplierId).orElseThrow(() ->
                new InvalidProductException("No supplier with id " + supplierId + "."));
        if (!supplier.active()) {
            throw new InvalidProductException(
                    "Supplier '" + supplier.name() + "' is inactive, so a product cannot be "
                            + "newly assigned to them.");
        }
    }

    /**
     * A price of zero is refused, null is not.
     *
     * <p>Null means "no price set", which is a real state for a product imported from an external
     * catalogue. Zero looks the same on a screen and produces an invoice line worth nothing without
     * anyone deciding to give the goods away, so the two must not be interchangeable.
     */
    private static void requireUsablePrice(Money sellingPrice) {
        if (sellingPrice != null && !sellingPrice.isPositive()) {
            throw new InvalidProductException(
                    "Selling price " + sellingPrice + " is not positive. Use no price at all for "
                            + "\"not priced yet\" — a zero price is a silently free sale.");
        }
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidProductException(what + " must not be blank.");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** True when a quantity has anything after the decimal point. */
    private static boolean hasFraction(BigDecimal quantity) {
        return quantity.stripTrailingZeros().scale() > 0;
    }

    /**
     * Projects a list, reading every product's last lot cost in one query rather than one per row.
     *
     * <p>The alternative is an N+1 that only shows up once the catalogue is real.
     */
    private List<ProductView> toViews(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, UnitCost> lastCosts = latestLotCostsByProductId();
        return products.stream()
                .map(product -> toView(product, lastCosts.get(product.getId())))
                .toList();
    }

    private ProductView toView(Product product) {
        UnitCost lastCost = lots
                .findFirstByProductIdOrderByAcquisitionDateDescIdDesc(product.getId())
                .map(InventoryLot::getUnitCost)
                .orElse(null);
        return toView(product, lastCost);
    }

    private Map<Long, UnitCost> latestLotCostsByProductId() {
        Map<Long, UnitCost> costs = new HashMap<>();
        for (Object[] row : lots.findLatestLotCostPerProduct()) {
            long productId = ((Number) row[0]).longValue();
            BigDecimal amount = (BigDecimal) row[1];
            Currency currency = Currency.getInstance(((String) row[2]).trim());
            costs.put(productId, UnitCost.of(amount, currency));
        }
        return costs;
    }

    /**
     * @param lastPurchaseCost the most recent lot's unit cost, or null where the product has never been
     *     received. Q6's answer — computed from lots, never a column, exactly like stock.
     *     <p><strong>Step 10 obligation.</strong> Brief §5 says a lot's unit cost includes allocated
     *     landed costs, so the day freight allocation exists this stops being the <em>purchase</em>
     *     price and has to come from the purchase invoice line instead. It is correct today because
     *     nothing allocates yet, and the day it stops being correct is knowable in advance.
     */
    private ProductView toView(Product product, UnitCost lastPurchaseCost) {
        return new ProductView(
                product.getId(),
                product.getSku(),
                product.getEan(),
                product.getName(),
                product.getType(),
                UnitOfMeasureServiceImpl.toView(product.getUnitOfMeasure()),
                product.getDefaultVatClassId(),
                product.getSellingPrice(),
                product.getSupplierId(),
                product.getSupplierSku(),
                product.isSerialTracked(),
                product.isBundle(),
                lastPurchaseCost,
                product.isActive(),
                // Nothing is hidden on the way out of the core. Redaction is applied by the
                // ...For methods, against a specific viewer.
                Set.of());
    }
}
