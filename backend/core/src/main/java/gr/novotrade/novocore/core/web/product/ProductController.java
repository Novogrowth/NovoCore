package gr.novotrade.novocore.core.web.product;

import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.StockLevels;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.CurrentUser;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.InvalidInputException;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.api.shared.Required;
import gr.novotrade.novocore.core.web.Requires;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Products.
 *
 * <h2>Every read here is redacted, and that is enforced rather than remembered</h2>
 *
 * <p>{@code ProductService} has plain reads for the core's own costing and posting rules — a FIFO
 * calculation cannot work from a blanked cost — and {@code ...For(viewer)} variants that apply
 * {@code ProductView.redactedFor}. {@code PROGRESS.md} flagged the risk in step 5 and named this
 * controller as the thing to review for it: <em>anything answering a request from a person must use
 * the {@code For} variants.</em>
 *
 * <p>So this class never calls a plain read, and {@code WebAuthorizationRulesTest} fails the build
 * if anything in {@code ..core.web..} does. Three fields are at stake — last purchase price,
 * supplier, and the supplier's own SKU — and Remote/Order Staff is the role they are kept from,
 * which is also the role most likely to be holding a scanner in front of this endpoint.
 *
 * <p>The four {@code For} variants this controller needs did not exist before step 14; adding them
 * to the service was the alternative to filtering a redacted list inside a controller, which would
 * have put domain logic in the web layer and filtered on a field that may have just been blanked.
 */
@RestController
@Requires(section = Section.PRODUCTS)
class ProductController {

    private final ProductService products;
    private final InventoryService inventory;
    private final CurrentUser currentUser;

    ProductController(ProductService products, InventoryService inventory, CurrentUser currentUser) {
        this.products = products;
        this.inventory = inventory;
        this.currentUser = currentUser;
    }

    // -------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------

    /**
     * Products, filtered, always redacted for the caller.
     *
     * <p>{@code sku} and {@code ean} are exact lookups and answer with a list of zero or one rather
     * than a 404. An unrecognised barcode is an ordinary outcome in brief §5's barcode-first flow —
     * it falls back to a supplier link and then to manual entry — so "no such product" is data here,
     * not an error.
     *
     * <p><strong>{@code search} is a fourth alternative lookup, and it did not replace {@code sku}
     * or {@code ean}.</strong> Both of those are exact by design and are what a barcode scanner and
     * an integration call; a scan that matched a <em>substring</em> of a barcode would put the wrong
     * product on an invoice. {@code search} is the filter box a person types into — SKU, name, brand,
     * barcode and the supplier's own code, matched anywhere in the string, case- and
     * accent-insensitively. It combines with {@code active}, which the others do not need to.
     */
    @GetMapping(path = "/api/products", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<ProductView> products(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String ean,
            @RequestParam(required = false) String search) {

        RoleView viewer = viewer();
        int lookups = (sku == null ? 0 : 1) + (ean == null ? 0 : 1) + (supplierId == null ? 0 : 1)
                + (search == null ? 0 : 1);
        if (lookups > 1) {
            throw new InvalidInputException(
                    "sku, ean, supplierId and search are alternative lookups; name one.");
        }

        if (sku != null) {
            return ListResponse.of(asList(products.findBySkuFor(sku, viewer)));
        }
        if (ean != null) {
            return ListResponse.of(asList(products.findByEanFor(ean, viewer)));
        }
        if (supplierId != null) {
            return ListResponse.of(products.bySupplierFor(supplierId, viewer));
        }
        if (search != null) {
            // searchFor, never search: it narrows the searched columns for a role that may not see
            // the supplier's SKU, which redaction alone would not do. See ProductService.searchFor.
            return ListResponse.of(
                    products.searchFor(search, Boolean.TRUE.equals(active), viewer));
        }
        return ListResponse.of(Boolean.TRUE.equals(active)
                ? products.activeFor(viewer)
                : products.allFor(viewer));
    }

    @GetMapping(path = "/api/products/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ProductView product(@PathVariable long id) {
        return products.requireFor(id, viewer());
    }

    /**
     * Stock per location, plus the computed sellable figure (Q7).
     *
     * <p><strong>Declared under {@code PRODUCTS}, not {@code INVENTORY}, deliberately.</strong>
     * {@code StockLevels} carries quantities and no cost. {@code Section.INVENTORY} exists to keep
     * unit costs away from the role that picks orders — an order picker with VIEW on Products needs
     * to know there are three left, and a <em>lot</em> is what carries what those three cost.
     * Putting this route behind INVENTORY would either stop Remote/Order Staff doing its job or
     * force a grant that hands over cost data with it.
     *
     * <p>Nothing is stored: this is computed on every read, and a bundle reports how many could be
     * assembled rather than a stock of its own.
     */
    @GetMapping(path = "/api/products/{id}/stock", produces = MediaType.APPLICATION_JSON_VALUE)
    StockLevels stock(@PathVariable long id) {
        // requireFor first: it is the call that refuses a role without Products access and a
        // product that does not exist, so stockOf is never reached for either.
        products.requireFor(id, viewer());
        return inventory.stockOf(id);
    }

    // -------------------------------------------------------------------------------------------
    // Changing
    // -------------------------------------------------------------------------------------------

    /**
     * There is no whole-object {@code PUT}, here or anywhere in this surface.
     *
     * <p>{@code ProductService} has nine named change operations and no {@code update(product)}. A
     * {@code PUT} taking a whole product would have to diff it and dispatch, which is domain logic
     * in a controller, and it would turn "the field was absent" into "set it to null".
     */
    @PostMapping(path = "/api/products",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    ProductView create(@RequestBody NewProduct request) {
        // Redacted on the way back out too: a role that may create a product but not see its
        // supplier must not receive the supplier in the creation response.
        return products.create(request).redactedFor(viewer());
    }

    @PatchMapping(path = "/api/products/{id}/name",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    ProductView rename(@PathVariable long id, @RequestBody NameRequest request) {
        return products.rename(id, request.name()).redactedFor(viewer());
    }

    /**
     * Sets or clears the selling price.
     *
     * <p>A null price is how "not priced yet" is said and is accepted; <strong>zero is refused</strong>
     * by the service. The two look identical on a screen and mean opposite things — zero produces an
     * invoice line worth nothing without anyone having chosen to give the goods away.
     */
    @PatchMapping(path = "/api/products/{id}/selling-price",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    ProductView changeSellingPrice(@PathVariable long id, @RequestBody SellingPriceRequest request) {
        return products.changeSellingPrice(id, request.sellingPrice()).redactedFor(viewer());
    }

    @PatchMapping(path = "/api/products/{id}/vat-class",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    ProductView changeVatClass(@PathVariable long id, @RequestBody VatClassRequest request) {
        return products.changeDefaultVatClass(id, request.vatClassId()).redactedFor(viewer());
    }

    /**
     * Sets or clears the supplier, and that supplier's own code for the product.
     *
     * <p>One route rather than two because they are one fact: a supplier SKU without a supplier is
     * refused in the service and by a CHECK constraint, so allowing them to be set separately would
     * create an order in which the pair is briefly illegal.
     */
    @PatchMapping(path = "/api/products/{id}/supplier",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    ProductView changeSupplier(@PathVariable long id, @RequestBody SupplierRequest request) {
        return products.changeSupplier(id, request.supplierId(), request.supplierSku())
                .redactedFor(viewer());
    }

    /**
     * Sets or clears the brand.
     *
     * <p>Null clears it. Blank is normalised to null by the service rather than stored, so "no
     * brand" has one representation in the column and a brand search cannot half-match a product
     * that has none.
     */
    @PatchMapping(path = "/api/products/{id}/brand",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    ProductView changeBrand(@PathVariable long id, @RequestBody BrandRequest request) {
        return products.changeBrand(id, request.brand()).redactedFor(viewer());
    }

    @PatchMapping(path = "/api/products/{id}/ean",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    ProductView changeEan(@PathVariable long id, @RequestBody EanRequest request) {
        return products.changeEan(id, request.ean()).redactedFor(viewer());
    }

    @PatchMapping(path = "/api/products/{id}/unit-of-measure",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    ProductView changeUnitOfMeasure(
            @PathVariable long id, @RequestBody UnitOfMeasureRequest request) {
        return products.changeUnitOfMeasure(id, request.unitOfMeasureId()).redactedFor(viewer());
    }

    /**
     * Turns individual serial tracking on or off.
     *
     * <p>Refused once the product has lots, and refused outright for a service or a bundle. Those
     * are the service's rules and this route does not restate them — it would be a second copy of a
     * decision that has to be enforced in the core anyway.
     */
    @PatchMapping(path = "/api/products/{id}/serial-tracking",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    ProductView changeSerialTracking(
            @PathVariable long id, @RequestBody SerialTrackingRequest request) {
        return products.changeSerialTracking(id, request.serialTracked()).redactedFor(viewer());
    }

    /** No delete, only deactivate — the same stance as the chart of accounts. */
    @PostMapping(path = "/api/products/{id}/deactivate")
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        products.deactivate(id);
    }

    @PostMapping(path = "/api/products/{id}/reactivate")
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        products.reactivate(id);
    }

    // -------------------------------------------------------------------------------------------

    private RoleView viewer() {
        return currentUser.require().role();
    }

    private static List<ProductView> asList(java.util.Optional<ProductView> product) {
        return product.map(List::of).orElseGet(List::of);
    }

    record NameRequest(String name) {
    }

    /** Null clears the price, which is how "not priced yet" is said. Zero is refused. */
    record SellingPriceRequest(Money sellingPrice) {
    }

    record VatClassRequest(long vatClassId) {
    }

    /** Both null clears the association; a SKU without a supplier is refused. */
    record SupplierRequest(Long supplierId, String supplierSku) {
    }

    record EanRequest(String ean) {
    }

    /** Null clears the brand, which is how "this product has no brand" is said. */
    record BrandRequest(String brand) {
    }

    record UnitOfMeasureRequest(long unitOfMeasureId) {
    }

    /**
     * ⚠️ <strong>Boxed in Q1 (2026-08-03), backend queue item 7 — and this is the field whose
     * primitive twin broke product creation for every user.</strong>
     *
     * <p>As a primitive it could not be null, so an omitted key was refused by
     * {@code FAIL_ON_NULL_FOR_PRIMITIVES} before any handler ran, with a message naming no field:
     * <em>"Cannot map null into type boolean"</em>. Boxed plus {@code Required.field} it answers
     * <em>"serialTracked" is required and was not supplied.</em> — the shape
     * {@link RoleController}'s {@code FieldRestrictionRequest} has had all along, and whose javadoc
     * names the worse failure this also removes: had {@code FAIL_ON_NULL_FOR_PRIMITIVES} been off, an
     * omitted flag would arrive as {@code false} and <strong>silently turn serial tracking off</strong>,
     * answering 200. The 400 was the lucky outcome; boxing removes the luck.
     */
    record SerialTrackingRequest(Boolean serialTracked) {

        SerialTrackingRequest {
            Required.field(serialTracked, "serialTracked");
        }
    }
}
