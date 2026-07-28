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
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.supplier.SupplierView;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
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
 */
@Service
class ProductServiceImpl implements ProductService {

    private static final String ENTITY_TYPE = "Product";

    private final ProductRepository repository;
    private final VatClassService vatClasses;
    private final SupplierService suppliers;
    private final AuditLogService auditLog;

    ProductServiceImpl(ProductRepository repository, VatClassService vatClasses,
            SupplierService suppliers, AuditLogService auditLog) {
        this.repository = repository;
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
        return repository.findById(id).map(ProductServiceImpl::toView);
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
        return repository.findBySkuIgnoreCase(normalised).map(ProductServiceImpl::toView);
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
        return repository.findByEan(normalised).map(ProductServiceImpl::toView);
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
        Objects.requireNonNull(viewer, "viewer");
        // Refused rather than returned empty. "You may not see products" and "there are no
        // products" are different answers, and an empty list cannot express the difference.
        viewer.requireView(Section.PRODUCTS);
        return active().stream().map(product -> product.redactedFor(viewer)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductView> findFor(long id, RoleView viewer) {
        Objects.requireNonNull(viewer, "viewer");
        viewer.requireView(Section.PRODUCTS);
        return find(id).map(product -> product.redactedFor(viewer));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductView requireFor(long id, RoleView viewer) {
        return findFor(id, viewer).orElseThrow(() -> new ProductNotFoundException(id));
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
        requireSupplierPair(request.supplierId(), supplierSku);
        requireUsablePrice(request.sellingPrice());

        Product saved = repository.save(new Product(
                sku, ean, name, request.type(), request.unitOfMeasure(),
                request.defaultVatClassId(), request.sellingPrice(),
                request.supplierId(), supplierSku));

        auditLog.record("product.created", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "sku", sku,
                "name", name,
                "type", request.type().name(),
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

    private static List<ProductView> toViews(List<Product> products) {
        return products.stream().map(ProductServiceImpl::toView).toList();
    }

    private static ProductView toView(Product product) {
        return new ProductView(
                product.getId(),
                product.getSku(),
                product.getEan(),
                product.getName(),
                product.getType(),
                product.getUnitOfMeasure(),
                product.getDefaultVatClassId(),
                product.getSellingPrice(),
                product.getSupplierId(),
                product.getSupplierSku(),
                // Derived from lot costs, which do not exist until step 6 (Q6). Null here rather
                // than a stored column, for the same reason stock is not stored.
                null,
                product.isActive(),
                // Nothing is hidden on the way out of the core. Redaction is applied by the
                // ...For methods, against a specific viewer.
                Set.of());
    }
}
